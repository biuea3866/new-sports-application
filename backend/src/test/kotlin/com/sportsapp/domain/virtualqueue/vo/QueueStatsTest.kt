package com.sportsapp.domain.virtualqueue.vo

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * `QueueStats.of` — 운영자 통계(FR-11) 도메인 표현 검증.
 *
 * waitingCount·admittedCount는 Store에서 즉시 조회 가능한 카운트를 그대로 담는다.
 * admissionRatePerSec·avgWaitSeconds·p95WaitSeconds는 시계열 지표로 Observability(BE-10,
 * `VirtualQueueMetricsBinder`)가 소유하며, BE-10이 아직 request-time 조회 경로를 제공하지 않으므로
 * 이 시점에는 0.0 placeholder로 채운다(stats 산출 방침, [VirtualQueueDomainService.stats] 참조).
 */
class QueueStatsTest : BehaviorSpec({

    Given("waitingCount=42, admittedCount=100인 상태에서") {
        When("QueueStats.of를 호출하면") {
            val stats = QueueStats.of(waitingCount = 42L, admittedCount = 100L)

            Then("Store 조회값을 그대로 담는다") {
                stats.waitingCount shouldBe 42L
                stats.admittedCount shouldBe 100L
            }

            Then("지표성 필드는 BE-10 미연동 상태라 0.0 placeholder다") {
                stats.admissionRatePerSec shouldBe 0.0
                stats.avgWaitSeconds shouldBe 0.0
                stats.p95WaitSeconds shouldBe 0.0
            }
        }
    }

    Given("대기 인원이 0건인 빈 큐 상태에서") {
        When("QueueStats.of를 호출하면") {
            val stats = QueueStats.of(waitingCount = 0L, admittedCount = 0L)

            Then("음수·예외 없이 0건으로 반환한다") {
                stats.waitingCount shouldBe 0L
                stats.admittedCount shouldBe 0L
            }
        }
    }
})
