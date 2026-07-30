package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val ALERT_ID = 42L

class ProcessAlertUseCaseTest : BehaviorSpec({

    Given("처리 대상 alertId") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = ProcessAlertUseCase(alertDomainService)

        every { alertDomainService.process(ALERT_ID) } returns Unit

        When("execute를 호출하면") {
            useCase.execute(ALERT_ID)

            Then("동일 alertId로 process를 1회 위임한다") {
                verify(exactly = 1) { alertDomainService.process(ALERT_ID) }
            }
        }
    }
})
