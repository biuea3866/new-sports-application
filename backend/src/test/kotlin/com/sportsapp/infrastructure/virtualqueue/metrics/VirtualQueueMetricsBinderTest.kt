package com.sportsapp.infrastructure.virtualqueue.metrics

import com.sportsapp.domain.virtualqueue.gateway.VirtualQueueStore
import com.sportsapp.domain.virtualqueue.vo.QueueTarget
import com.sportsapp.domain.virtualqueue.vo.QueueTargetType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import java.util.concurrent.TimeUnit

/**
 * `VirtualQueueMetricsBinder` 게이지·타이머 바인딩 단위 검증(BE-10).
 *
 * `VirtualQueueStore`는 MockK로 대체해 순수 바인딩·집계 로직만 얇게 검증한다 — 실제 Redis 상태
 * 연동 검증은 `VirtualQueueStoreImplTest`(infra 통합)가 이미 커버한다.
 */
class VirtualQueueMetricsBinderTest : BehaviorSpec({

    val limitedDropTarget = QueueTarget(QueueTargetType.LIMITED_DROP, 1L)
    val ticketingTarget = QueueTarget(QueueTargetType.TICKETING_EVENT, 2L)

    Given("활성 대상 2건의 waitingSize가 각각 30·45일 때") {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns setOf(limitedDropTarget, ticketingTarget)
        every { store.waitingSize(limitedDropTarget) } returns 30L
        every { store.waitingSize(ticketingTarget) } returns 45L
        every { store.admittedCount(any()) } returns 0L
        val meterRegistry = SimpleMeterRegistry()
        val binder = VirtualQueueMetricsBinder(meterRegistry, store)

        When("게이지를 바인딩하면") {
            binder.bindMeters()

            Then("virtual_queue.length 게이지는 활성 대상의 waitingSize 합(75)을 노출한다") {
                meterRegistry.get(VirtualQueueMetricsBinder.QUEUE_LENGTH_GAUGE).gauge().value() shouldBe 75.0
            }

            Then("virtual_queue.length 게이지는 소스 태그 virtual-queue를 갖는다") {
                meterRegistry.get(VirtualQueueMetricsBinder.QUEUE_LENGTH_GAUGE).gauge().id
                    .getTag(VirtualQueueMetricsBinder.SOURCE_TAG) shouldBe VirtualQueueMetricsBinder.SOURCE_VALUE
            }
        }
    }

    Given("활성 대상이 0건일 때") {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns emptySet()
        val meterRegistry = SimpleMeterRegistry()
        val binder = VirtualQueueMetricsBinder(meterRegistry, store)

        When("게이지를 바인딩하면") {
            binder.bindMeters()

            Then("virtual_queue.length 게이지는 0을 반환한다 (빈 상태 엣지)") {
                meterRegistry.get(VirtualQueueMetricsBinder.QUEUE_LENGTH_GAUGE).gauge().value() shouldBe 0.0
            }
        }
    }

    Given("admission이 전진하는 대상 1건에 대해") {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns setOf(limitedDropTarget)
        every { store.waitingSize(limitedDropTarget) } returns 0L
        every { store.admittedCount(limitedDropTarget) } returnsMany listOf(0L, 200L)
        val meterRegistry = SimpleMeterRegistry()
        val binder = VirtualQueueMetricsBinder(meterRegistry, store)
        binder.bindMeters()

        When("바인딩 직후 첫 조회와, 전진 이벤트 이후 재조회를 하면") {
            val firstRate = meterRegistry.get(VirtualQueueMetricsBinder.ADMISSION_RATE_GAUGE).gauge().value()
            Thread.sleep(50)
            val secondRate = meterRegistry.get(VirtualQueueMetricsBinder.ADMISSION_RATE_GAUGE).gauge().value()

            Then("첫 조회는 기준점만 세워 0.0이다") {
                firstRate shouldBe 0.0
            }

            Then("admission_rate 지표가 전진 이벤트마다 갱신된다 (두번째 조회는 0보다 크다)") {
                secondRate shouldBeGreaterThan 0.0
            }
        }
    }

    Given("wait_seconds Timer가 바인딩된 상태에서") {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns emptySet()
        val meterRegistry = SimpleMeterRegistry()
        val binder = VirtualQueueMetricsBinder(meterRegistry, store)
        binder.bindMeters()

        When("1초~10초 사이 대기시간 10건을 기록하면") {
            (1..10).forEach { seconds -> binder.recordWaitSeconds(seconds.toDouble()) }

            Then("wait_seconds Timer가 P95를 산출한다 (P95 >= P50 > 0)") {
                val timer = meterRegistry.get(VirtualQueueMetricsBinder.WAIT_SECONDS_TIMER).timer()
                val percentileValues = timer.takeSnapshot().percentileValues()
                val p50 = requireNotNull(percentileValues.find { it.percentile() == 0.5 }) { "P50 미산출" }
                val p95 = requireNotNull(percentileValues.find { it.percentile() == 0.95 }) { "P95 미산출" }

                p50.value(TimeUnit.SECONDS) shouldBeGreaterThan 0.0
                p95.value(TimeUnit.SECONDS) shouldBeGreaterThanOrEqual p50.value(TimeUnit.SECONDS)
            }
        }
    }

    Given("음수 delta(admittedCount 감소·대상 리셋)가 발생해도") {
        val store = mockk<VirtualQueueStore>()
        every { store.activeTargets() } returns setOf(limitedDropTarget)
        every { store.waitingSize(limitedDropTarget) } returns 0L
        every { store.admittedCount(limitedDropTarget) } returnsMany listOf(500L, 100L)
        val meterRegistry = SimpleMeterRegistry()
        val binder = VirtualQueueMetricsBinder(meterRegistry, store)
        binder.bindMeters()

        When("연속 조회하면") {
            meterRegistry.get(VirtualQueueMetricsBinder.ADMISSION_RATE_GAUGE).gauge().value()
            Thread.sleep(20)
            val rate = meterRegistry.get(VirtualQueueMetricsBinder.ADMISSION_RATE_GAUGE).gauge().value()

            Then("admission_rate는 음수로 떨어지지 않고 0으로 바닥 처리된다") {
                rate shouldBe 0.0
            }
        }
    }
})
