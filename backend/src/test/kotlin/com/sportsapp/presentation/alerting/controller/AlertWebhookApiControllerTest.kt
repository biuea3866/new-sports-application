package com.sportsapp.presentation.alerting.controller

import com.sportsapp.application.alerting.dto.GrafanaWebhookCommand
import com.sportsapp.application.alerting.usecase.ReceiveGrafanaAlertUseCase
import com.sportsapp.application.alerting.usecase.RaiseAlertUseCase
import com.sportsapp.domain.alerting.dto.RaiseAlertCommand
import com.sportsapp.domain.alerting.entity.Alert
import com.sportsapp.domain.alerting.vo.AlertSeverity
import com.sportsapp.domain.alerting.vo.AlertSource
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.slot
import io.mockk.every
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val WEBHOOK_TOKEN = "shared-secret"

private fun buildMockMvc(
    receiveGrafanaAlertUseCase: ReceiveGrafanaAlertUseCase,
    raiseAlertUseCase: RaiseAlertUseCase,
    webhookToken: String = WEBHOOK_TOKEN,
    alertingEnabled: Boolean = true,
) = MockMvcBuilders.standaloneSetup(
    AlertWebhookApiController(
        receiveGrafanaAlertUseCase = receiveGrafanaAlertUseCase,
        raiseAlertUseCase = raiseAlertUseCase,
        webhookToken = webhookToken,
        alertingEnabled = alertingEnabled,
    ),
).setControllerAdvice(GlobalExceptionHandler()).build()

private fun grafanaWebhookBody(
    endpoint: String = "/pay",
    source: String = "latency",
    severity: String = "warn",
    env: String = "prod",
): String = """
    {
      "alerts": [
        {
          "labels": {
            "alertname": "HighLatency",
            "endpoint": "$endpoint",
            "source": "$source",
            "severity": "$severity",
            "env": "$env"
          },
          "annotations": { "summary": "P95 초과" }
        }
      ]
    }
""".trimIndent()

private fun raiseAlertBody(
    endpoint: String = "/orders",
    source: String = "deployment",
    severity: String = "critical",
    env: String = "prod",
): String = """
    {
      "endpoint": "$endpoint",
      "source": "$source",
      "severity": "$severity",
      "env": "$env"
    }
""".trimIndent()

class AlertWebhookApiControllerTest : BehaviorSpec({

    Given("유효한 Authorization: Bearer 헤더와 Grafana webhook 페이로드") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)
        val alert = mockk<Alert>()
        every { receiveGrafanaAlertUseCase.execute(any()) } returns alert

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .header("Authorization", "Bearer $WEBHOOK_TOKEN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grafanaWebhookBody()),
            )

            Then("202 Accepted를 반환하고 ReceiveGrafanaAlertUseCase를 호출한다") {
                result.andExpect(status().isAccepted)
                verify(exactly = 1) { receiveGrafanaAlertUseCase.execute(any()) }
            }
        }
    }

    Given("grafana 경로에 Authorization 헤더가 없는 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grafanaWebhookBody()),
            )

            Then("401을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isUnauthorized)
                verify(exactly = 0) { receiveGrafanaAlertUseCase.execute(any()) }
            }
        }
    }

    Given("grafana 경로에 잘못된 Bearer 토큰이 담긴 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .header("Authorization", "Bearer wrong-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grafanaWebhookBody()),
            )

            Then("401을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isUnauthorized)
                verify(exactly = 0) { receiveGrafanaAlertUseCase.execute(any()) }
            }
        }
    }

    Given("내부 raise 경로에 X-Alert-Token 헤더가 없는 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(raiseAlertBody()),
            )

            Then("401을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isUnauthorized)
                verify(exactly = 0) { raiseAlertUseCase.execute(any()) }
            }
        }
    }

    Given("내부 raise 경로에 잘못된 X-Alert-Token이 담긴 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts")
                    .header("X-Alert-Token", "wrong-token")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(raiseAlertBody()),
            )

            Then("401을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isUnauthorized)
                verify(exactly = 0) { raiseAlertUseCase.execute(any()) }
            }
        }
    }

    Given("source=deployment인 유효한 내부 raise 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)
        val commandSlot = slot<RaiseAlertCommand>()
        val alert = mockk<Alert>()
        every { raiseAlertUseCase.execute(capture(commandSlot)) } returns alert

        When("POST /internal/alerts 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts")
                    .header("X-Alert-Token", WEBHOOK_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(raiseAlertBody(endpoint = "/orders", source = "deployment", severity = "critical", env = "prod")),
            )

            Then("202를 반환하고 RaiseAlertUseCase로 매핑돼 위임된다") {
                result.andExpect(status().isAccepted)
                verify(exactly = 1) { raiseAlertUseCase.execute(any()) }
                commandSlot.captured.endpoint shouldBe "/orders"
                commandSlot.captured.source shouldBe AlertSource.DEPLOYMENT
                commandSlot.captured.severity shouldBe AlertSeverity.CRITICAL
                commandSlot.captured.env shouldBe "prod"
            }
        }
    }

    Given("endpoint/source/severity/env label을 담은 Grafana webhook 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)
        val commandSlot = slot<GrafanaWebhookCommand>()
        val alert = mockk<Alert>()
        every { receiveGrafanaAlertUseCase.execute(capture(commandSlot)) } returns alert

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .header("Authorization", "Bearer $WEBHOOK_TOKEN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grafanaWebhookBody(endpoint = "/pay", source = "latency", severity = "warn", env = "prod")),
            )

            Then("labels가 GrafanaWebhookCommand 필드로 정확히 변환된다") {
                result.andExpect(status().isAccepted)
                commandSlot.captured.endpoint shouldBe "/pay"
                commandSlot.captured.source shouldBe "latency"
                commandSlot.captured.severity shouldBe "warn"
                commandSlot.captured.env shouldBe "prod"
            }
        }
    }

    Given("깨진 JSON 본문을 담은 Grafana webhook 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .header("Authorization", "Bearer $WEBHOOK_TOKEN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"alerts": [ { "labels": { "endpoint": "/pay" """),
            )

            Then("400을 반환한다") {
                result.andExpect(status().isBadRequest)
            }
        }
    }

    Given("무효한 source enum 값을 담은 내부 raise 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase)

        When("POST /internal/alerts 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts")
                    .header("X-Alert-Token", WEBHOOK_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(raiseAlertBody(source = "not_a_valid_source")),
            )

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { raiseAlertUseCase.execute(any()) }
            }
        }
    }

    Given("alerting.enabled=false인 상태에서 유효한 grafana webhook 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase, alertingEnabled = false)

        When("POST /internal/alerts/grafana 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts/grafana")
                    .header("Authorization", "Bearer $WEBHOOK_TOKEN")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(grafanaWebhookBody()),
            )

            Then("202를 반환하지만 UseCase는 호출하지 않는다") {
                result.andExpect(status().isAccepted)
                verify(exactly = 0) { receiveGrafanaAlertUseCase.execute(any()) }
            }
        }
    }

    Given("alerting.enabled=false인 상태에서 유효한 내부 raise 요청") {
        val receiveGrafanaAlertUseCase = mockk<ReceiveGrafanaAlertUseCase>()
        val raiseAlertUseCase = mockk<RaiseAlertUseCase>()
        val mockMvc = buildMockMvc(receiveGrafanaAlertUseCase, raiseAlertUseCase, alertingEnabled = false)

        When("POST /internal/alerts 요청 시") {
            val result = mockMvc.perform(
                post("/internal/alerts")
                    .header("X-Alert-Token", WEBHOOK_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(raiseAlertBody()),
            )

            Then("202를 반환하지만 UseCase는 호출하지 않는다") {
                result.andExpect(status().isAccepted)
                verify(exactly = 0) { raiseAlertUseCase.execute(any()) }
            }
        }
    }
})
