package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.exception.QueueEntryNotFoundException
import com.sportsapp.domain.virtualqueue.exception.QueueFullException
import com.sportsapp.domain.virtualqueue.gateway.EntryTokenIssuer
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueuePosition
import com.sportsapp.domain.virtualqueue.vo.QueueStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

/**
 * 가상 대기열 진입·상태조회·이탈 오케스트레이션 (BE-04).
 *
 * `VirtualQueueStore`·`EntryTokenIssuer`·`FeatureFlagEvaluator`(전부 domain interface)만
 * 주입한다 — infrastructure 미참조. 플래그 분기·admission 판정·fail-open 전부 이 서비스가
 * 캡슐화하며, UseCase(후행 티켓)는 이 서비스의 메서드 하나만 호출한다.
 *
 * 대기 없이 즉시 통과(직접 입장)하는 두 경로 — 플래그 OFF, Redis 장애 fail-open — 모두
 * [EntryTokenIssuer.mintStateless]로 발급한다(`issueIfAbsent`는 쓰지 않는다). 플래그 OFF 경로는
 * 인터셉터가 애초에 토큰 검증을 스킵하므로 `issueIfAbsent`의 멱등 마커(`SET NX`, Redis 의존)가
 * 불필요하고, Redis가 죽은 상태에서 호출하면 `DataAccessException`이 그대로 전파돼 5xx가 된다.
 * fail-open 경로 역시 같은 이유(`issueIfAbsent`가 Redis 장애로 재실패)로 mintStateless를 쓴다
 * (redis-contract §0-3). 안전 근거는 Redis 게이트가 아니라 MySQL 최종 정합 방어
 * (`Stock.@Version`·유니크 제약, §0-4)다.
 *
 * Redis 장애(`DataAccessException`) 관측(`virtual_queue.redis_degraded`)은 infra
 * (`VirtualQueueStoreImpl.executeTracked`) 한 곳에서만 수행한다 — 이 서비스가 같은 예외를 다시
 * 세면 단일 장애가 두 번 집계돼 알람 임계가 왜곡된다. 이 서비스는 fail-open 분기(폴백 호출)만
 * 담당하고 계측 책임은 갖지 않는다.
 */
@Service
class VirtualQueueDomainService(
    private val virtualQueueStore: VirtualQueueStore,
    private val entryTokenIssuer: EntryTokenIssuer,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
    @Value("\${virtual-queue.max-capacity:100000}") private val maxCapacity: Int,
    @Value("\${virtual-queue.admission.batch-size:100}") private val batchSize: Int,
    @Value("\${virtual-queue.admission.tick-seconds:2}") private val tickSeconds: Int,
) {

    /** 대기열 진입. 플래그 OFF면 대기 없이 즉시 통과, ON이면 멱등 진입 + 포화 거부(FR-7). */
    fun enter(target: QueueTarget, userId: Long): QueueStatus {
        if (!isQueueEnabled(userId)) return directEntry(target, userId)
        return try {
            enterQueue(target, userId)
        } catch (exception: DataAccessException) {
            directEntry(target, userId)
        }
    }

    /** 순번·상태 조회. 폴링이 heartbeat를 겸하고, 고정 seq 기준으로 admission을 판정한다(§0-1). */
    fun status(target: QueueTarget, userId: Long): QueueStatus =
        try {
            pollStatus(target, userId)
        } catch (exception: DataAccessException) {
            directEntry(target, userId)
        }

    /** 명시적 이탈. */
    fun leave(target: QueueTarget, userId: Long) {
        virtualQueueStore.leave(target, userId)
    }

    private fun isQueueEnabled(userId: Long): Boolean =
        featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ENABLED, FeatureContext.of(userId), false)

    private fun enterQueue(target: QueueTarget, userId: Long): QueueStatus {
        val seq = virtualQueueStore.enterIfAbsent(target, userId, maxCapacity) ?: throw QueueFullException(target)
        virtualQueueStore.registerActive(target)
        return QueueStatus.waiting(positionOf(target, userId, seq))
    }

    private fun pollStatus(target: QueueTarget, userId: Long): QueueStatus {
        virtualQueueStore.touchHeartbeat(target, userId)
        val seq = virtualQueueStore.seqOf(target, userId) ?: throw QueueEntryNotFoundException(target, userId)
        val position = positionOf(target, userId, seq)
        if (!position.admitted) return QueueStatus.waiting(position)
        return admitEntry(target, userId)
    }

    private fun admitEntry(target: QueueTarget, userId: Long): QueueStatus {
        val entryToken = entryTokenIssuer.issueIfAbsent(target, userId)
        virtualQueueStore.leave(target, userId)
        return QueueStatus.admitted(entryToken)
    }

    private fun positionOf(target: QueueTarget, userId: Long, seq: Long): QueuePosition {
        val rank = virtualQueueStore.rankOf(target, userId) ?: 0
        val admittedCount = virtualQueueStore.admittedCount(target)
        return QueuePosition.of(
            rank = rank,
            seq = seq,
            admittedCount = admittedCount,
            batchSize = batchSize,
            tickSeconds = tickSeconds,
        )
    }

    /**
     * 대기 없이 즉시 통과 — 플래그 OFF·fail-open 공통 경로. [EntryTokenIssuer.mintStateless]로만
     * 발급해 이 경로에서 Redis에 접근하지 않는다(클래스 문서 참조).
     */
    private fun directEntry(target: QueueTarget, userId: Long): QueueStatus {
        val entryToken = entryTokenIssuer.mintStateless(target, userId)
        return QueueStatus.directEntry(entryToken)
    }
}
