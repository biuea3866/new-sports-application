package com.sportsapp.domain.virtualqueue.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.virtualqueue.VirtualQueueFeatureFlagKeys
import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * `AdmissionDomainService.runBatch` — 배치 admission 전진(`advanceAdmission`)과 이탈 방출
 * (`sweepStale`)을 검증한다 (BE-05, redis-contract §0-2/§2).
 */
class AdmissionDomainServiceTest : BehaviorSpec({

    val virtualQueueStore = mockk<VirtualQueueStore>()
    val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
    val service = AdmissionDomainService(virtualQueueStore, featureFlagEvaluator)
    val target = QueueTarget(QueueTargetType.LIMITED_DROP, 1L)

    Given("배치 admission 전진 대상과 이탈 대상이 함께 있는 큐") {
        every { virtualQueueStore.seqExists(target) } returns true
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
        every { virtualQueueStore.seqExists(target) } returns true
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
        every { virtualQueueStore.seqExists(target) } returns true
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

    Given("seq 키가 만료된(죽은) 대상 — 폴링·이탈 모두 끊긴 상태 (seq-존재 가드)") {
        val deadTarget = QueueTarget(QueueTargetType.LIMITED_DROP, 2L)
        every { virtualQueueStore.seqExists(deadTarget) } returns false
        every { virtualQueueStore.deactivate(deadTarget) } just runs

        When("runBatch를 호출하면") {
            val result = service.runBatch(deadTarget, batchSize = 100, staleSeconds = 60, maxEvictPerTick = 500)

            Then("advanceAdmission·sweepStale은 호출하지 않고 deactivate로 queue:active에서 제거한다") {
                result.deactivated shouldBe true
                result.admittedCount shouldBe 0L
                result.evictedCount shouldBe 0
                verify(exactly = 1) { virtualQueueStore.deactivate(deadTarget) }
                verify(exactly = 0) { virtualQueueStore.advanceAdmission(deadTarget, any()) }
                verify(exactly = 0) { virtualQueueStore.sweepStale(deadTarget, any(), any()) }
            }
        }
    }

    Given("활성 대상이 2건 등록된 queue:active") {
        val activeTargets = setOf(
            QueueTarget(QueueTargetType.LIMITED_DROP, 10L),
            QueueTarget(QueueTargetType.TICKETING_EVENT, 20L),
        )
        every { virtualQueueStore.activeTargets() } returns activeTargets

        When("activeTargets를 호출하면") {
            val result = service.activeTargets()

            Then("VirtualQueueStore.activeTargets 결과를 그대로 반환한다") {
                result shouldBe activeTargets
                verify(exactly = 1) { virtualQueueStore.activeTargets() }
            }
        }
    }

    Given("Admission Pump 킬 스위치 플래그가 ON으로 평가되는 상태에서") {
        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED, FeatureContext.anonymous(), true)
        } returns true

        When("isPumpEnabled를 호출하면") {
            val result = service.isPumpEnabled()

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("Admission Pump 킬 스위치 플래그가 OFF로 평가되는 상태에서 (운영 킬 스위치)") {
        every {
            featureFlagEvaluator.isEnabled(VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED, FeatureContext.anonymous(), true)
        } returns false

        When("isPumpEnabled를 호출하면") {
            val result = service.isPumpEnabled()

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }
})
