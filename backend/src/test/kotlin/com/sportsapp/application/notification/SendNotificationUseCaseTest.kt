package com.sportsapp.application.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
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

    Given("[U-01] 유효한 templateId 와 payload 로 발송 요청") {
        val command = SendNotificationCommand(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "payment-completed",
            payload = mapOf("amount" to 30000),
        )

        val notification = mockk<Notification> {
            every { id } returns 1L
            every { userId } returns 1L
            every { channel } returns NotificationChannel.IN_APP
            every { templateId } returns "payment-completed"
            every { status } returns NotificationStatus.SENT
            every { sentAt } returns baseTime
            every { readAt } returns null
            every { createdAt } returns baseTime
        }

        every {
            notificationDomainService.sendWithTemplate(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "payment-completed",
                payload = mapOf("amount" to 30000),
            )
        } returns notification

        When("execute 를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] NotificationResponse 가 반환되고 sendWithTemplate 이 호출된다") {
                result.templateId shouldBe "payment-completed"
                result.userId shouldBe 1L
                result.status shouldBe NotificationStatus.SENT
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

    Given("[U-02] 존재하지 않는 templateId 로 발송 요청") {
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
            Then("[U-02] UnknownTemplateException 이 전파된다") {
                shouldThrow<UnknownTemplateException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
