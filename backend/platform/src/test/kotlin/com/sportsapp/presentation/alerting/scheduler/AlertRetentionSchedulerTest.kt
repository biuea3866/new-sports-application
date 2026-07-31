package com.sportsapp.presentation.alerting.scheduler

import com.sportsapp.application.alerting.usecase.PurgeExpiredAlertsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AlertRetentionSchedulerTest : BehaviorSpec({

    Given("삭제 대상이 있는 정리 배치 cron 트리거") {
        val purgeExpiredAlertsUseCase = mockk<PurgeExpiredAlertsUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = AlertRetentionScheduler(purgeExpiredAlertsUseCase, meterRegistry)

        every { purgeExpiredAlertsUseCase.execute() } returns 4L

        When("purgeExpiredAlerts를 호출하면") {
            scheduler.purgeExpiredAlerts()

            Then("PurgeExpiredAlertsUseCase를 1회 호출하고 삭제 건수만큼 카운터를 증가시킨다") {
                verify(exactly = 1) { purgeExpiredAlertsUseCase.execute() }
                meterRegistry.counter("alerting_retention_purged_total").count() shouldBe 4.0
            }
        }
    }

    Given("삭제 대상이 없는 정리 배치 cron 트리거") {
        val purgeExpiredAlertsUseCase = mockk<PurgeExpiredAlertsUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = AlertRetentionScheduler(purgeExpiredAlertsUseCase, meterRegistry)

        every { purgeExpiredAlertsUseCase.execute() } returns 0L

        When("purgeExpiredAlerts를 호출하면") {
            scheduler.purgeExpiredAlerts()

            Then("카운터를 증가시키지 않는다") {
                meterRegistry.counter("alerting_retention_purged_total").count() shouldBe 0.0
            }
        }
    }
})
