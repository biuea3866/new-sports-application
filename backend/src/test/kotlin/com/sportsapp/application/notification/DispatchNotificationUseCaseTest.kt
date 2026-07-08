package com.sportsapp.application.notification

import com.sportsapp.application.notification.usecase.DispatchNotificationUseCase
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.domain.notification.gateway.SendResult
import com.sportsapp.domain.notification.gateway.TemplateRenderer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class DispatchNotificationUseCaseTest : BehaviorSpec({

    data class Mocks(
        val notificationRepository: NotificationRepository = mockk(),
        val notificationCustomRepository: NotificationCustomRepository = mockk(relaxed = true),
        val templateRenderer: TemplateRenderer = mockk(relaxed = true),
        val domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
        val channelGateway: NotificationChannelGateway = mockk(),
    ) {
        val domainService = NotificationDomainService(
            notificationRepository = notificationRepository,
            notificationCustomRepository = notificationCustomRepository,
            channelGateways = listOf(channelGateway),
            templateRenderer = templateRenderer,
            domainEventPublisher = domainEventPublisher,
        )
        val useCase = DispatchNotificationUseCase(domainService)
    }

    Given("QUEUED 상태이고 지원 게이트웨이가 있는 알림") {
        val mocks = Mocks()
        val notification = Notification(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "welcome",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        every { mocks.channelGateway.supportedChannel } returns NotificationChannel.IN_APP
        every { mocks.channelGateway.send(notification) } returns SendResult(success = true, errorMessage = null)
        every { mocks.notificationRepository.findById(1L) } returns notification
        every { mocks.notificationRepository.save(notification) } returns notification

        When("DispatchNotificationUseCase.execute 를 호출하면") {
            mocks.useCase.execute(1L)

            Then("gateway.send 가 호출되고 알림 상태가 SENT 로 변경된다") {
                verify(exactly = 1) { mocks.channelGateway.send(notification) }
                notification.status shouldBe NotificationStatus.SENT
            }
        }
    }

    Given("QUEUED 상태이지만 지원 게이트웨이가 없는 알림") {
        val mocks = Mocks()
        val notification = Notification(
            userId = 2L,
            channel = NotificationChannel.EMAIL,
            templateId = "email-notice",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        every { mocks.channelGateway.supportedChannel } returns NotificationChannel.IN_APP
        every { mocks.notificationRepository.findById(2L) } returns notification
        every { mocks.notificationRepository.save(notification) } returns notification

        When("DispatchNotificationUseCase.execute 를 호출하면") {
            mocks.useCase.execute(2L)

            Then("gateway.send 는 호출되지 않고 상태가 FAILED 로 변경된다") {
                verify(exactly = 0) { mocks.channelGateway.send(any()) }
                notification.status shouldBe NotificationStatus.FAILED
            }
        }
    }

    Given("gateway 발송이 실패하는 알림") {
        val mocks = Mocks()
        val notification = Notification(
            userId = 3L,
            channel = NotificationChannel.IN_APP,
            templateId = "promo",
            payload = NotificationPayload(emptyMap()),
            status = NotificationStatus.QUEUED,
            sentAt = null,
            readAt = null,
            eventId = null,
        )
        every { mocks.channelGateway.supportedChannel } returns NotificationChannel.IN_APP
        every { mocks.channelGateway.send(notification) } returns SendResult(success = false, errorMessage = "timeout")
        every { mocks.notificationRepository.findById(3L) } returns notification
        every { mocks.notificationRepository.save(notification) } returns notification

        When("DispatchNotificationUseCase.execute 를 호출하면") {
            mocks.useCase.execute(3L)

            Then("알림 상태가 FAILED 로 변경된다") {
                notification.status shouldBe NotificationStatus.FAILED
            }
        }
    }

    Given("notificationId 에 해당하는 알림이 없는 경우") {
        val mocks = Mocks()
        every { mocks.notificationRepository.findById(999L) } returns null

        When("DispatchNotificationUseCase.execute 를 호출하면") {
            mocks.useCase.execute(999L)

            Then("예외 없이 조용히 종료된다") {
                verify(exactly = 0) { mocks.notificationRepository.save(any()) }
            }
        }
    }
})
