package com.sportsapp.application.notification.usecase

import com.sportsapp.application.notification.dto.SendRawNotificationCommand
import com.sportsapp.domain.notification.dto.NotificationResult
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

private const val USER_ID = 1L
private const val TEMPLATE_ID = "alerting.discord"

class SendRawNotificationUseCaseTest : BehaviorSpec({

    val notificationDomainService = mockk<NotificationDomainService>()
    val useCase = SendRawNotificationUseCase(notificationDomainService)

    val baseTime = ZonedDateTime.now(ZoneOffset.UTC)

    Given("이미 렌더된 _title/_body를 담은 payload로 발송 요청") {
        val payload = mapOf(
            "_title" to "결제 API 지연",
            "_body" to "P95 latency 2500ms 초과",
            "source" to "LATENCY",
            "severity" to "CRITICAL",
            "env" to "prod",
        )
        val command = SendRawNotificationCommand(
            userId = USER_ID,
            channel = NotificationChannel.DISCORD,
            templateId = TEMPLATE_ID,
            payload = payload,
        )
        val notificationResult = NotificationResult(
            id = 1L,
            userId = USER_ID,
            channel = NotificationChannel.DISCORD,
            templateId = TEMPLATE_ID,
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            createdAt = baseTime,
        )

        every {
            notificationDomainService.send(
                userId = USER_ID,
                channel = NotificationChannel.DISCORD,
                templateId = TEMPLATE_ID,
                payload = NotificationPayload(payload),
            )
        } returns notificationResult

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("템플릿 렌더링 없이 원본 payload 그대로 NotificationDomainService.send가 호출된다") {
                result.status shouldBe NotificationStatus.QUEUED
                result.channel shouldBe NotificationChannel.DISCORD
                verify(exactly = 1) {
                    notificationDomainService.send(
                        userId = USER_ID,
                        channel = NotificationChannel.DISCORD,
                        templateId = TEMPLATE_ID,
                        payload = NotificationPayload(payload),
                    )
                }
            }
        }
    }
})
