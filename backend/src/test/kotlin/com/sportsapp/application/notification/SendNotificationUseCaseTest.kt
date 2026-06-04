package com.sportsapp.application.notification

import com.sportsapp.application.notification.SendNotificationCommand
import com.sportsapp.application.notification.usecase.SendNotificationUseCase
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationResult
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.domain.notification.UnknownTemplateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SendNotificationUseCaseTest : BehaviorSpec({

    val notificationDomainService = mockk<NotificationDomainService>()
    val useCase = SendNotificationUseCase(notificationDomainService)

    val baseTime = ZonedDateTime.now(ZoneOffset.UTC)

    Given("유효한 templateId 와 payload 로 발송 요청") {
        val command = SendNotificationCommand(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "payment-completed",
            payload = mapOf("amount" to 30000),
        )

        val notificationResult = NotificationResult(
            id = 1L,
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "payment-completed",
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            createdAt = baseTime,
        )

        every {
            notificationDomainService.sendWithTemplate(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "payment-completed",
                payload = mapOf("amount" to 30000),
            )
        } returns notificationResult

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("NotificationResponse 가 반환되고 sendWithTemplate 이 호출된다") {
                result.templateId shouldBe "payment-completed"
                result.userId shouldBe 1L
                result.status shouldBe NotificationStatus.QUEUED
                verify(exactly = 1) {
                    notificationDomainService.sendWithTemplate(
                        userId = 1L,
                        channel = NotificationChannel.IN_APP,
                        templateId = "payment-completed",
                        payload = mapOf("amount" to 30000),
                    )
                }
            }
        }
    }

    Given("존재하지 않는 templateId 로 발송 요청") {
        val command = SendNotificationCommand(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "non-existent",
            payload = emptyMap(),
        )

        every {
            notificationDomainService.sendWithTemplate(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "non-existent",
                payload = emptyMap(),
            )
        } throws UnknownTemplateException("non-existent")

        When("execute 를 호출하면") {
            Then("UnknownTemplateException 이 전파된다") {
                shouldThrow<UnknownTemplateException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
