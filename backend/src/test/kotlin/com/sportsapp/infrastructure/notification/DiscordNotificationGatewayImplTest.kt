package com.sportsapp.infrastructure.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.infrastructure.external.ExternalContractSupport
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import com.sportsapp.infrastructure.notification.gateway.DiscordNotificationGatewayImpl
import com.sportsapp.infrastructure.notification.gateway.DiscordProperties
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

private val objectMapper = ObjectMapper()

private fun gatewayFor(mockWebServer: MockWebServer): DiscordNotificationGatewayImpl {
    val properties = DiscordProperties(
        webhookUrl = mockWebServer.url("/").toString(),
        username = "SportsApp Alert",
    )
    return DiscordNotificationGatewayImpl(ExternalRestClientFactory(), properties)
}

private fun alertNotification(
    severity: String? = "CRITICAL",
    source: String? = "prometheus",
    env: String? = "production",
): Notification = Notification.queue(
    userId = 1L,
    channel = NotificationChannel.DISCORD,
    templateId = "alert-triggered",
    payload = NotificationPayload(
        buildMap {
            put("_title", "장애 발생")
            put("_body", "CPU 90% 초과")
            severity?.let { put("severity", it) }
            source?.let { put("source", it) }
            env?.let { put("env", it) }
        },
    ),
)

/**
 * DiscordNotificationGatewayImpl 계약 테스트 (BE-02).
 * mock webhook 서버로 Embed 페이로드를 캡처해 필드·색상 매핑을 검증한다.
 */
class DiscordNotificationGatewayImplTest : BehaviorSpec({

    Given("DISCORD 채널 알림을 발송하면") {
        When("send 를 호출하면") {
            Then("Embed 에 제목/본문/source/severity/env 필드가 포함되고 성공을 반환한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(204))
                val gateway = gatewayFor(mockWebServer)

                val result = gateway.send(alertNotification())

                result.success shouldBe true
                val recordedRequest = mockWebServer.takeRequest()
                val body = objectMapper.readTree(recordedRequest.body.readUtf8())
                val embed = body.get("embeds")[0]
                embed.get("title").asText() shouldBe "장애 발생"
                embed.get("description").asText() shouldBe "CPU 90% 초과"
                val fieldValues = embed.get("fields").map { it.get("name").asText() to it.get("value").asText() }
                fieldValues shouldBe listOf(
                    "source" to "prometheus",
                    "severity" to "CRITICAL",
                    "env" to "production",
                )

                mockWebServer.shutdown()
            }
        }
    }

    Given("webhook 이 5xx 를 반환하면") {
        When("send 를 호출하면") {
            Then("SendResult(success=false) 를 반환하고 예외를 전파하지 않는다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(500))
                val gateway = gatewayFor(mockWebServer)

                val result = gateway.send(alertNotification())

                result.success shouldBe false
                result.errorMessage shouldNotBe null

                mockWebServer.shutdown()
            }
        }
    }

    Given("severity 가 CRITICAL 이면") {
        When("WARN, INFO 와 비교하면") {
            Then("Embed color 가 서로 다르게 매핑된다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                mockWebServer.enqueue(MockResponse().setResponseCode(204))
                mockWebServer.enqueue(MockResponse().setResponseCode(204))
                mockWebServer.enqueue(MockResponse().setResponseCode(204))
                val gateway = gatewayFor(mockWebServer)

                gateway.send(alertNotification(severity = "CRITICAL"))
                gateway.send(alertNotification(severity = "WARN"))
                gateway.send(alertNotification(severity = "INFO"))

                val criticalColor = objectMapper.readTree(mockWebServer.takeRequest().body.readUtf8())
                    .get("embeds")[0].get("color").asInt()
                val warnColor = objectMapper.readTree(mockWebServer.takeRequest().body.readUtf8())
                    .get("embeds")[0].get("color").asInt()
                val infoColor = objectMapper.readTree(mockWebServer.takeRequest().body.readUtf8())
                    .get("embeds")[0].get("color").asInt()

                criticalColor shouldNotBe warnColor
                criticalColor shouldNotBe infoColor
                warnColor shouldNotBe infoColor

                mockWebServer.shutdown()
            }
        }
    }

    Given("NotificationChannelGateway 계약") {
        When("supportedChannel 을 조회하면") {
            Then("DISCORD 를 반환한다") {
                val mockWebServer = ExternalContractSupport.startMockServer()
                val gateway = gatewayFor(mockWebServer)

                gateway.supportedChannel shouldBe NotificationChannel.DISCORD

                mockWebServer.shutdown()
            }
        }
    }
})
