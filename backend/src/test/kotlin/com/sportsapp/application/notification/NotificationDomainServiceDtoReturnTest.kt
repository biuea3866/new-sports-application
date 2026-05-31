package com.sportsapp.application.notification

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationCustomRepository
import com.sportsapp.domain.notification.NotificationDispatchRequestedEvent
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationNotOwnedException
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationResult
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.domain.notification.RenderedNotification
import com.sportsapp.domain.notification.TemplateRenderer
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NotificationDomainServiceDtoReturnTest : BehaviorSpec({

    val notificationRepository = mockk<NotificationRepository>()
    val notificationCustomRepository = mockk<NotificationCustomRepository>()
    val templateRenderer = mockk<TemplateRenderer>()
    val domainEventPublisher = mockk<DomainEventPublisher>()

    val service = NotificationDomainService(
        notificationRepository = notificationRepository,
        notificationCustomRepository = notificationCustomRepository,
        channelGateways = emptyList(),
        templateRenderer = templateRenderer,
        domainEventPublisher = domainEventPublisher,
    )

    val baseTime = ZonedDateTime.now(ZoneOffset.UTC)

    fun queuedNotificationMock(userId: Long) = mockk<Notification> {
        every { id } returns 1L
        every { this@mockk.userId } returns userId
        every { channel } returns NotificationChannel.IN_APP
        every { templateId } returns "welcome"
        every { status } returns NotificationStatus.QUEUED
        every { sentAt } returns null
        every { readAt } returns null
        every { createdAt } returns baseTime
    }

    Given("IN_APP 채널로 send 를 호출하는 상황") {
        val publishedEventSlot = slot<NotificationDispatchRequestedEvent>()
        justRun { domainEventPublisher.publish(capture(publishedEventSlot)) }
        every { templateRenderer.render(any(), any()) } returns RenderedNotification("제목", "본문")

        When("send 를 호출하면") {
            val queuedMock = queuedNotificationMock(userId = 1L)
            every { notificationRepository.save(any()) } returns queuedMock

            val result = service.send(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "welcome",
                payload = NotificationPayload(emptyMap()),
            )

            Then("NotificationResult 가 반환되고 status 가 QUEUED 이다 — 발송은 AFTER_COMMIT 에서 수행된다") {
                result.shouldBeInstanceOf<NotificationResult>()
                result.status shouldBe NotificationStatus.QUEUED
                result.userId shouldBe 1L
            }

            Then("NotificationDispatchRequestedEvent 가 발행된다") {
                verify(exactly = 1) { domainEventPublisher.publish(any()) }
                publishedEventSlot.captured.notificationId shouldBe 1L
            }
        }

        When("sendWithTemplate 을 호출하면") {
            val queuedMock = queuedNotificationMock(userId = 2L)
            every { notificationRepository.save(any()) } returns queuedMock

            val result = service.sendWithTemplate(
                userId = 2L,
                channel = NotificationChannel.IN_APP,
                templateId = "welcome",
                payload = mapOf("key" to "value"),
            )

            Then("NotificationResult 가 반환되고 status 가 QUEUED 이다") {
                result.shouldBeInstanceOf<NotificationResult>()
                result.status shouldBe NotificationStatus.QUEUED
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

            Then("NotificationResult 가 반환되고 readAt 이 null 이 아니다") {
                result.shouldBeInstanceOf<NotificationResult>()
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
