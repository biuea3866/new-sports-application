package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.UnsupportedChannelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class EnqueueNotificationUseCaseTest : BehaviorSpec({

    val notificationDomainService = mockk<NotificationDomainService>()
    val useCase = EnqueueNotificationUseCase(notificationDomainService)

    Given("[U-01] 미지원 채널(PUSH)로 알림 발송 요청 시") {
        val command = EnqueueNotificationCommand(
            channel = NotificationChannel.PUSH,
            templateId = "payment-completed",
            payload = NotificationPayload(mapOf("amount" to "10000")),
            recipientUserId = 1L,
            eventId = "payment-123",
        )
        every {
            notificationDomainService.enqueueOrSkip(any(), any(), any(), any(), any())
        } throws UnsupportedChannelException(NotificationChannel.PUSH)

        When("execute 를 호출하면") {
            Then("[U-01] UnsupportedChannelException 을 던진다") {
                shouldThrow<UnsupportedChannelException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("[U-02] 동일 eventId 의 알림이 이미 존재하는 경우") {
        val command = EnqueueNotificationCommand(
            channel = NotificationChannel.IN_APP,
            templateId = "payment-completed",
            payload = NotificationPayload(mapOf("amount" to "10000")),
            recipientUserId = 1L,
            eventId = "payment-dup-456",
        )
        every {
            notificationDomainService.enqueueOrSkip(
                eventId = "payment-dup-456",
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "payment-completed",
                payload = NotificationPayload(mapOf("amount" to "10000")),
            )
        } returns null

        When("execute 를 호출하면") {
            useCase.execute(command)

            Then("[U-02] DomainService 의 enqueueOrSkip 이 1회 호출되고 예외 없이 완료된다") {
                verify(exactly = 1) {
                    notificationDomainService.enqueueOrSkip(
                        eventId = "payment-dup-456",
                        userId = 1L,
                        channel = NotificationChannel.IN_APP,
                        templateId = "payment-completed",
                        payload = any(),
                    )
                }
            }
        }
    }
})
