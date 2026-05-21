package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationCustomRepository
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationNotOwnedException
import com.sportsapp.domain.notification.NotificationNotFoundException
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.domain.notification.TemplateRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class MarkNotificationReadUseCaseTest : BehaviorSpec({

    val notificationRepository = mockk<NotificationRepository>()
    val lNotificationCustomRepository = mockk<NotificationCustomRepository>()
    val templateRenderer = mockk<TemplateRenderer>()
    val notificationDomainService = NotificationDomainService(
        notificationRepository = notificationRepository,
        lNotificationCustomRepository = lNotificationCustomRepository,
        channelGateways = emptyList(),
        templateRenderer = templateRenderer,
    )

    Given("다른 사용자(userId=999)가 소유한 알림") {
        val notification = Notification(
            userId = 999L,
            channel = NotificationChannel.IN_APP,
            templateId = "test",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.SENT,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        every { notificationRepository.findById(1L) } returns notification

        When("userId=1 사용자가 읽음 처리를 시도하면") {
            Then("[U-01] NotificationNotOwnedException 이 발생한다") {
                shouldThrow<NotificationNotOwnedException> {
                    notificationDomainService.markRead(notificationId = 1L, userId = 1L)
                }
            }
        }
    }

    Given("userId=1 이 소유하고 이미 읽은 알림") {
        val originalReadAt = ZonedDateTime.now().minusHours(1)
        val notification = Notification(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "test",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.SENT,
            sentAt = null,
            readAt = originalReadAt,
            eventId = null,
        )
        every { notificationRepository.findById(2L) } returns notification
        every { notificationRepository.save(any()) } answers { firstArg() }

        When("다시 읽음 처리를 호출하면") {
            val result = notificationDomainService.markRead(notificationId = 2L, userId = 1L)

            Then("[U-02] readAt 은 변경되지 않고 멱등하게 처리된다") {
                result.readAt.shouldNotBeNull()
                result.readAt shouldBe originalReadAt
                verify(exactly = 1) { notificationRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 알림 ID") {
        every { notificationRepository.findById(99L) } returns null

        When("읽음 처리를 호출하면") {
            Then("NotificationNotFoundException 이 발생한다") {
                shouldThrow<NotificationNotFoundException> {
                    notificationDomainService.markRead(notificationId = 99L, userId = 1L)
                }
            }
        }
    }
})
