package com.sportsapp.application.alerting.usecase

import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.service.AlertDomainService
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

private const val ENDPOINT = "/orders"
private const val ENV = "prod"

class RaiseAlertUseCaseTest : BehaviorSpec({

    Given("source=oversell인 내부 raise Command") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = RaiseAlertUseCase(alertDomainService)
        val command = RaiseAlertCommand(
            endpoint = ENDPOINT,
            source = AlertSource.OVERSELL,
            severity = AlertSeverity.CRITICAL,
            env = ENV,
        )
        val alert = mockk<Alert>()

        every { alertDomainService.raise(command) } returns alert

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("Command를 변형 없이 그대로 AlertDomainService.raise에 전달한다") {
                result shouldBe alert
                verify(exactly = 1) { alertDomainService.raise(command) }
            }
        }
    }

    Given("쿨다운 억제로 raise가 null을 반환하는 상황") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = RaiseAlertUseCase(alertDomainService)
        val command = RaiseAlertCommand(
            endpoint = ENDPOINT,
            source = AlertSource.DEPLOYMENT,
            severity = AlertSeverity.CRITICAL,
            env = ENV,
        )

        every { alertDomainService.raise(command) } returns null

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("null을 그대로 반환한다") {
                result.shouldBeNull()
            }
        }
    }
})
