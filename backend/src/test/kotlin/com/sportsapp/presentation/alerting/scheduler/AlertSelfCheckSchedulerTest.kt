package com.sportsapp.presentation.alerting.scheduler

import com.sportsapp.application.alerting.usecase.SendSelfCheckUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AlertSelfCheckSchedulerTest : BehaviorSpec({

    Given("self-check cron 트리거 시각") {
        val sendSelfCheckUseCase = mockk<SendSelfCheckUseCase>()
        val scheduler = AlertSelfCheckScheduler(sendSelfCheckUseCase)

        every { sendSelfCheckUseCase.execute() } returns Unit

        When("runSelfCheck를 호출하면") {
            scheduler.runSelfCheck()

            Then("SendSelfCheckUseCase를 1회 호출한다") {
                verify(exactly = 1) { sendSelfCheckUseCase.execute() }
            }
        }
    }
})
