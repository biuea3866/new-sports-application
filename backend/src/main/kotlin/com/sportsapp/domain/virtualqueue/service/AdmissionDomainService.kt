package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.dto.AdmissionBatchResult
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import java.time.ZonedDateTime
import org.springframework.stereotype.Service

/**
 * 한 대상(`QueueTarget`)의 배치 admission 전진과 이탈 방출을 담당한다 (BE-05).
 *
 * 클러스터 단일 실행 보장은 이 서비스가 아니라 호출부(스케줄러, BE-07)의 분산 락이 담당한다 —
 * 이 서비스는 락을 잡은 뒤 호출되는 순수 배치 로직이다. at-least-once 틱에도 멱등하다
 * (`advanceAdmission`의 seq 상한, `sweepStale`의 ZREM no-op).
 */
@Service
class AdmissionDomainService(
    private val virtualQueueStore: VirtualQueueStore,
    private val featureFlagEvaluator: FeatureFlagEvaluator,
) {

    /** `queue:active` 전체 조회 — 스케줄러(`AdmissionPumpScheduler`)가 순회할 활성 대상 목록. */
    fun activeTargets(): Set<QueueTarget> = virtualQueueStore.activeTargets()

    /**
     * Admission Pump 운영 킬 스위치 판정 — 매 틱 런타임 조회(no-conditional-on-property).
     * 정의되지 않은 키·ARCHIVED 플래그는 [FeatureFlagEvaluator] 계약대로 기본값(true, 활성)을 반환한다.
     */
    fun isPumpEnabled(): Boolean =
        featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED, FeatureContext.anonymous(), true)

    /**
     * ⓪ seq-존재 가드: seq 키가 만료된 죽은 대상이면 `advanceAdmission`을
     * 호출하지 않고 `queue:active`에서 제거만 한 뒤 즉시 반환한다 — seq 만료 후 `admit.lua`가 돌면
     * `admitted_count = min(count+batch, 0) = 0`으로 고수위가 역행/붕괴하기 때문이다(폴링이 지속되는
     * 한 seq는 생존하므로, 이 가드는 폴링·이탈 모두 끊긴 대상만 정리한다).
     * ① `advanceAdmission`으로 admitted_count를 batchSize만큼 전진(상한=seq, redis-contract §0-2)
     * ② `sweepStale`로 heartbeat가 `now-staleSeconds` 이전인 member를 `maxEvictPerTick` 상한 내
     * 방출한다(FR-8, redis-contract §2 — 대량 동시 이탈 시 Redis 단일 스레드 블로킹 방지).
     */
    fun runBatch(target: QueueTarget, batchSize: Int, staleSeconds: Long, maxEvictPerTick: Int): AdmissionBatchResult {
        if (!virtualQueueStore.seqExists(target)) {
            virtualQueueStore.deactivate(target)
            return AdmissionBatchResult(admittedCount = 0L, evictedCount = 0, deactivated = true)
        }
        val admittedCount = virtualQueueStore.advanceAdmission(target, batchSize)
        val staleBeforeEpochMs = ZonedDateTime.now().minusSeconds(staleSeconds).toInstant().toEpochMilli()
        val evictedCount = virtualQueueStore.sweepStale(target, staleBeforeEpochMs, maxEvictPerTick)
        return AdmissionBatchResult(admittedCount, evictedCount)
    }
}
