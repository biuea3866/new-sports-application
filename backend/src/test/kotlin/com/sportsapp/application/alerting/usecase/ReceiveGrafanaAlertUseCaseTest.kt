package com.sportsapp.application.alerting.usecase

import com.sportsapp.application.alerting.dto.GrafanaWebhookCommand
import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.service.AlertDomainService
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify

private const val ENDPOINT = "/pay"
private const val ENV = "prod"

class ReceiveGrafanaAlertUseCaseTest : BehaviorSpec({

    Given("정상 source/severity 값을 담은 Grafana webhook Command") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = ReceiveGrafanaAlertUseCase(alertDomainService)
        val command = GrafanaWebhookCommand(
            endpoint = ENDPOINT,
            source = "latency",
            severity = "warn",
            env = ENV,
            contextHint = "P95 초과",
        )
        val commandSlot = slot<RaiseAlertCommand>()
        val alert = mockk<Alert>()

        every { alertDomainService.raise(capture(commandSlot)) } returns alert

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("문자열을 enum으로 파싱해 AlertDomainService.raise에 위임한다") {
                result shouldBe alert
                commandSlot.captured.endpoint shouldBe ENDPOINT
                commandSlot.captured.source shouldBe AlertSource.LATENCY
                commandSlot.captured.severity shouldBe AlertSeverity.WARN
                commandSlot.captured.env shouldBe ENV
                commandSlot.captured.contextHint shouldBe "P95 초과"
                verify(exactly = 1) { alertDomainService.raise(any()) }
            }
        }
    }

    Given("알 수 없는 source 문자열을 담은 Grafana webhook Command") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = ReceiveGrafanaAlertUseCase(alertDomainService)
        val command = GrafanaWebhookCommand(
            endpoint = ENDPOINT,
            source = "unknown_source",
            severity = "warn",
            env = ENV,
        )

        When("execute를 호출하면") {
            Then("파싱 단계에서 예외를 던지고 raise를 호출하지 않는다") {
                shouldThrow<IllegalArgumentException> { useCase.execute(command) }
                verify(exactly = 0) { alertDomainService.raise(any()) }
            }
        }
    }

    Given("알 수 없는 severity 문자열을 담은 Grafana webhook Command") {
        val alertDomainService = mockk<AlertDomainService>()
        val useCase = ReceiveGrafanaAlertUseCase(alertDomainService)
        val command = GrafanaWebhookCommand(
            endpoint = ENDPOINT,
            source = "latency",
            severity = "unknown_severity",
            env = ENV,
        )

        When("execute를 호출하면") {
            Then("파싱 단계에서 예외를 던지고 raise를 호출하지 않는다") {
                shouldThrow<IllegalArgumentException> { useCase.execute(command) }
                verify(exactly = 0) { alertDomainService.raise(any()) }
            }
        }
    }
})
