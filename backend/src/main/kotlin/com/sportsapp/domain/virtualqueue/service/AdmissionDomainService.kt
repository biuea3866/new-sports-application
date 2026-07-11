package com.sportsapp.domain.virtualqueue.service

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
) {

    /**
     * ① `advanceAdmission`으로 admitted_count를 batchSize만큼 전진(상한=seq, redis-contract §0-2)
     * ② `sweepStale`로 heartbeat가 `now-staleSeconds` 이전인 member를 `maxEvictPerTick` 상한 내
     * 방출한다(FR-8, redis-contract §2 — 대량 동시 이탈 시 Redis 단일 스레드 블로킹 방지).
     */
    fun runBatch(target: QueueTarget, batchSize: Int, staleSeconds: Long, maxEvictPerTick: Int): AdmissionBatchResult {
        val admittedCount = virtualQueueStore.advanceAdmission(target, batchSize)
        val staleBeforeEpochMs = ZonedDateTime.now().minusSeconds(staleSeconds).toInstant().toEpochMilli()
        val evictedCount = virtualQueueStore.sweepStale(target, staleBeforeEpochMs, maxEvictPerTick)
        return AdmissionBatchResult(admittedCount, evictedCount)
    }
}
