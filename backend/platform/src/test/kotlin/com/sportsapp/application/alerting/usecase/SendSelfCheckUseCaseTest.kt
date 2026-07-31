package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.service.AlertDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class SendSelfCheckUseCaseTest : BehaviorSpec({

    Given("1시간 주기 self-check 트리거") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = SendSelfCheckUseCase(alertDomainService)

        every { alertDomainService.selfCheck() } returns Unit

        When("execute를 호출하면") {
            useCase.execute()

            Then("selfCheck를 1회 호출한다") {
                verify(exactly = 1) { alertDomainService.selfCheck() }
            }
        }
    }
})
