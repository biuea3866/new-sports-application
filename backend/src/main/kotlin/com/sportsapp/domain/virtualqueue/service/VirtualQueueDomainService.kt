package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.exception.QueueEntryNotFoundException
import com.sportsapp.domain.virtualqueue.exception.QueueFullException
import com.sportsapp.domain.virtualqueue.gateway.EntryTokenIssuer
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.EntryToken
import com.sportsapp.domain.virtualqueue.vo.QueuePosition
import com.sportsapp.domain.virtualqueue.vo.QueueStatus
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import io.micrometer.core.instrument.MeterRegistry
import org.springframework.beans.factory.annotation.Value
import org.springframework.dao.DataAccessException
import org.springframework.stereotype.Service

private const val REDIS_DEGRADED_COUNTER = "virtual_queue.redis_degraded"

/**
 * 가상 대기열 진입·상태조회·이탈 오케스트레이션 (BE-04).
 *
 * `VirtualQueueStore`·`EntryTokenIssuer`·`FeatureFlagEvaluator`(전부 domain interface)만
 * 주입한다 — infrastructure 미참조. 플래그 분기·admission 판정·fail-open 전부 이 서비스가
 * 캡슐화하며, UseCase(후행 티켓)는 이 서비스의 메서드 하나만 호출한다.
 *
 * fail-open(Redis 장애) 시 폴백 토큰은 [EntryTokenIssuer.issueIfAbsent]가 아니라
 * [EntryTokenIssuer.mintStateless]로 발급한다 — `issueIfAbsent`의 멱등 마커(`SET NX`)가 Redis
 * 의존이라 장애 상황에서 재실패하기 때문이다(redis-contract §0-3). 안전 근거는 Redis 게이트가
 * 아니라 MySQL 최종 정합 방어(`Stock.@Version`·유니크 제약, §0-4)다.
 */
@Service
class VirtualQueueDomainService(
    private val virtualQueueStore: VirtualQueueStore,
    private val entryTokenIssuer: EntryTokenIssuer,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
    private val meterRegistry: MeterRegistry,
    @Value("\${virtual-queue.max-capacity:100000}") private val maxCapacity: Int,
    @Value("\${virtual-queue.admission.batch-size:100}") private val batchSize: Int,
    @Value("\${virtual-queue.admission.tick-seconds:2}") private val tickSeconds: Int,
) {

    /** 대기열 진입. 플래그 OFF면 대기 없이 즉시 통과, ON이면 멱등 진입 + 포화 거부(FR-7). */
    fun enter(target: QueueTarget, userId: Long): QueueStatus {
        if (!isQueueEnabled(userId)) return directEntry(target, userId, viaFailOpen = false)
        return try {
            enterQueue(target, userId)
        } catch (exception: DataAccessException) {
            recordRedisDegraded()
            directEntry(target, userId, viaFailOpen = true)
        }
    }

    /** 순번·상태 조회. 폴링이 heartbeat를 겸하고, 고정 seq 기준으로 admission을 판정한다(§0-1). */
    fun status(target: QueueTarget, userId: Long): QueueStatus =
        try {
            pollStatus(target, userId)
        } catch (exception: DataAccessException) {
            recordRedisDegraded()
            directEntry(target, userId, viaFailOpen = true)
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
     * 대기 없이 즉시 통과. [viaFailOpen]이 true면 Redis 장애 폴백이라 [EntryTokenIssuer.mintStateless]
     * (Redis 미접근)를, false면 플래그 OFF 정상 경로라 [EntryTokenIssuer.issueIfAbsent](멱등 마커)를 쓴다.
     */
    private fun directEntry(target: QueueTarget, userId: Long, viaFailOpen: Boolean): QueueStatus {
        val entryToken = issueDirectEntryToken(target, userId, viaFailOpen)
        return QueueStatus.directEntry(entryToken)
    }

    private fun issueDirectEntryToken(target: QueueTarget, userId: Long, viaFailOpen: Boolean): EntryToken =
        if (viaFailOpen) entryTokenIssuer.mintStateless(target, userId)
        else entryTokenIssuer.issueIfAbsent(target, userId)

    private fun recordRedisDegraded() {
        meterRegistry.counter(REDIS_DEGRADED_COUNTER).increment()
    }
}
