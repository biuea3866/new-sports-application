package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * `AdmissionDomainService.runBatch` — 배치 admission 전진(`advanceAdmission`)과 이탈 방출
 * (`sweepStale`)을 검증한다 (BE-05, redis-contract §0-2/§2).
 */
class AdmissionDomainServiceTest : BehaviorSpec({

    val virtualQueueStore = mockk<VirtualQueueStore>()
    val service = AdmissionDomainService(virtualQueueStore)
    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 1L)

    Given("배치 admission 전진 대상과 이탈 대상이 함께 있는 큐") {
        every { virtualQueueStore.advanceAdmission(target, 100) } returns 200L
        every { virtualQueueStore.sweepStale(target, any(), 500) } returns 3

        When("runBatch(batchSize=100, staleSeconds=60, maxEvictPerTick=500)를 호출하면") {
            val beforeCutoffEpochMs = ZonedDateTime.now().minusSeconds(60).toInstant().toEpochMilli()
            val result = service.runBatch(target, batchSize = 100, staleSeconds = 60, maxEvictPerTick = 500)
            val afterCutoffEpochMs = ZonedDateTime.now().minusSeconds(60).toInstant().toEpochMilli()

            Then("advanceAdmission을 batchSize로 호출하고 신규 admitted_count를 반환한다") {
                result.admittedCount shouldBe 200L
                verify(exactly = 1) { virtualQueueStore.advanceAdmission(target, 100) }
            }

            Then("sweepStale을 now-staleSeconds cutoff·maxEvictPerTick 상한으로 호출한다") {
                result.evictedCount shouldBe 3
                val cutoffSlot = slot<Long>()
                verify(exactly = 1) { virtualQueueStore.sweepStale(target, capture(cutoffSlot), 500) }
                cutoffSlot.captured shouldBeGreaterThanOrEqual beforeCutoffEpochMs
                cutoffSlot.captured shouldBeLessThanOrEqual afterCutoffEpochMs
            }
        }
    }

    Given("방출 대상이 0건인 빈 큐") {
        every { virtualQueueStore.advanceAdmission(target, 50) } returns 50L
        every { virtualQueueStore.sweepStale(target, any(), 200) } returns 0

        When("runBatch(batchSize=50, maxEvictPerTick=200)를 호출하면") {
            val result = service.runBatch(target, batchSize = 50, staleSeconds = 60, maxEvictPerTick = 200)

            Then("예외 없이 전진 수만 반환한다") {
                result.admittedCount shouldBe 50L
                result.evictedCount shouldBe 0
            }
        }
    }

    Given("틱이 연속 2회 호출되는 상황 (at-least-once 멱등)") {
        every { virtualQueueStore.advanceAdmission(target, 30) } returns 90L
        every { virtualQueueStore.sweepStale(target, any(), 10) } returns 0

        When("runBatch(batchSize=30, maxEvictPerTick=10)를 연속 2회 호출하면") {
            val firstResult = service.runBatch(target, batchSize = 30, staleSeconds = 60, maxEvictPerTick = 10)
            val secondResult = service.runBatch(target, batchSize = 30, staleSeconds = 60, maxEvictPerTick = 10)

            Then("두 번 다 store가 반환하는 seq 상한값(90)에 수렴한다") {
                firstResult.admittedCount shouldBe 90L
                secondResult.admittedCount shouldBe 90L
                verify(exactly = 2) { virtualQueueStore.advanceAdmission(target, 30) }
            }
        }
    }
})
