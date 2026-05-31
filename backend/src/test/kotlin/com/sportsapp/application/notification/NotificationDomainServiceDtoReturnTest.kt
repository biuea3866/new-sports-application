package com.sportsapp.application.notification

import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationChannelGateway
import com.sportsapp.domain.notification.NotificationCustomRepository
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationNotOwnedException
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.RenderedNotification
import com.sportsapp.domain.notification.SendResult
import com.sportsapp.domain.notification.TemplateRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NotificationDomainServiceDtoReturnTest : BehaviorSpec({

    val notificationRepository = mockk<NotificationRepository>()
    val notificationCustomRepository = mockk<NotificationCustomRepository>()
    val channelGateway = mockk<NotificationChannelGateway>()
    val templateRenderer = mockk<TemplateRenderer>()

    val service = NotificationDomainService(
        notificationRepository = notificationRepository,
        notificationCustomRepository = notificationCustomRepository,
        channelGateways = listOf(channelGateway),
        templateRenderer = templateRenderer,
    )

    val baseTime = ZonedDateTime.now(ZoneOffset.UTC)

    fun sentNotificationMock(userId: Long, sentAt: ZonedDateTime) = mockk<Notification> {
        every { id } returns 1L
        every { this@mockk.userId } returns userId
        every { channel } returns NotificationChannel.IN_APP
        every { templateId } returns "welcome"
        every { status } returns NotificationStatus.SENT
        every { this@mockk.sentAt } returns sentAt
        every { readAt } returns null
        every { createdAt } returns baseTime
    }

    fun failedNotificationMock(userId: Long) = mockk<Notification> {
        every { id } returns 2L
        every { this@mockk.userId } returns userId
        every { channel } returns NotificationChannel.IN_APP
        every { templateId } returns "welcome"
        every { status } returns NotificationStatus.FAILED
        every { sentAt } returns null
        every { readAt } returns null
        every { createdAt } returns baseTime
    }

    Given("IN_APP 게이트웨이 발송이 성공하는 상황") {
        every { channelGateway.supportedChannel } returns NotificationChannel.IN_APP
        every { channelGateway.send(any()) } returns SendResult(success = true, errorMessage = null)
        every { templateRenderer.render(any(), any()) } returns RenderedNotification("제목", "본문")

        When("send 를 호출하면") {
            val savedNotification = sentNotificationMock(userId = 1L, sentAt = baseTime)
            every { notificationRepository.save(any()) } returnsMany listOf(
                mockk(relaxed = true) { every { channel } returns NotificationChannel.IN_APP },
                savedNotification,
            )

            val result = service.send(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "welcome",
                payload = NotificationPayload(emptyMap()),
            )

            Then("NotificationResponse 가 반환되고 status 가 SENT 이다") {
                result.status shouldBe NotificationStatus.SENT
                result.userId shouldBe 1L
                result.sentAt.shouldNotBeNull()
            }
        }

        When("sendWithTemplate 을 호출하면") {
            val savedNotification = sentNotificationMock(userId = 2L, sentAt = baseTime)
            every { notificationRepository.save(any()) } returnsMany listOf(
                mockk(relaxed = true) { every { channel } returns NotificationChannel.IN_APP },
                savedNotification,
            )

            val result = service.sendWithTemplate(
                userId = 2L,
                channel = NotificationChannel.IN_APP,
                templateId = "welcome",
                payload = mapOf("key" to "value"),
            )

            Then("NotificationResponse 가 반환되고 status 가 SENT 이다") {
                result.status shouldBe NotificationStatus.SENT
            }
        }
    }

    Given("IN_APP 게이트웨이 발송이 실패하는 상황") {
        every { channelGateway.supportedChannel } returns NotificationChannel.IN_APP
        every { channelGateway.send(any()) } returns SendResult(success = false, errorMessage = null)

        When("send 를 호출하면") {
            val failedMock = failedNotificationMock(userId = 3L)
            every { notificationRepository.save(any()) } returnsMany listOf(
                mockk(relaxed = true) { every { channel } returns NotificationChannel.IN_APP },
                failedMock,
            )

            val result = service.send(
                userId = 3L,
                channel = NotificationChannel.IN_APP,
                templateId = "welcome",
                payload = null,
            )

            Then("NotificationResponse 가 반환되고 status 가 FAILED 이다") {
                result.status shouldBe NotificationStatus.FAILED
                result.userId shouldBe 3L
            }
        }
    }

    Given("소유자 userId 와 일치하는 알림") {
        val readAt = baseTime
        val notification = Notification(
            userId = 10L,
            channel = NotificationChannel.IN_APP,
            templateId = "test",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.SENT,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        val savedMock = mockk<Notification> {
            every { id } returns 5L
            every { userId } returns 10L
            every { channel } returns NotificationChannel.IN_APP
            every { templateId } returns "test"
            every { status } returns NotificationStatus.SENT
            every { sentAt } returns null
            every { this@mockk.readAt } returns readAt
            every { createdAt } returns baseTime
        }
        every { notificationRepository.findById(5L) } returns notification
        every { notificationRepository.save(any()) } returns savedMock

        When("markRead 를 호출하면") {
            val result = service.markRead(notificationId = 5L, userId = 10L)

            Then("NotificationResponse 가 반환되고 readAt 이 null 이 아니다") {
                result.readAt.shouldNotBeNull()
                result.userId shouldBe 10L
            }
        }
    }

    Given("소유자가 아닌 userId 로 markRead 호출") {
        val notification = Notification(
            userId = 10L,
            channel = NotificationChannel.IN_APP,
            templateId = "test",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.SENT,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        every { notificationRepository.findById(6L) } returns notification

        When("다른 userId 로 markRead 를 호출하면") {
            Then("NotificationNotOwnedException 이 발생한다") {
                shouldThrow<NotificationNotOwnedException> {
                    service.markRead(notificationId = 6L, userId = 99L)
                }
            }
        }
    }
})
