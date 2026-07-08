package com.sportsapp.domain.notification

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.gateway.SendResult
import com.sportsapp.domain.notification.gateway.TemplateRenderer
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

private fun fakeGateway(channel: NotificationChannel, result: SendResult): NotificationChannelGateway =
    object : NotificationChannelGateway {
        override val supportedChannel: NotificationChannel = channel
        override fun send(notification: Notification): SendResult = result
    }

/**
 * DISCORD 채널 편입이 dispatchById 의 supportedChannel 라우팅에 자동 편입되고,
 * 기존 채널 라우팅을 깨지 않는지 확인하는 회귀 테스트 (BE-02).
 */
class NotificationDomainServiceDispatchTest : BehaviorSpec({

    Given("DISCORD 채널 Notification 과 DISCORD 를 지원하는 gateway 가 등록된 상황") {
        val notificationRepository = mockk<NotificationRepository>()
        val notificationCustomRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val discordGateway = fakeGateway(NotificationChannel.DISCORD, SendResult(success = true, errorMessage = null))
        val smsGateway = fakeGateway(NotificationChannel.SMS, SendResult(success = true, errorMessage = null))

        val service = NotificationDomainService(
            notificationRepository = notificationRepository,
            notificationCustomRepository = notificationCustomRepository,
            channelGateways = listOf(discordGateway, smsGateway),
            templateRenderer = templateRenderer,
            domainEventPublisher = domainEventPublisher,
        )

        val notification = Notification.queue(
            userId = 1L,
            channel = NotificationChannel.DISCORD,
            templateId = "alert-triggered",
            payload = NotificationPayload(mapOf("_title" to "장애", "_body" to "본문")),
        )
        every { notificationRepository.findById(1L) } returns notification
        every { notificationRepository.save(any()) } returns notification

        When("dispatchById 를 호출하면") {
            service.dispatchById(1L)

            Then("supportedChannel 이 DISCORD 인 gateway 가 선택되어 발송에 성공하고 SENT 로 전이된다") {
                notification.status shouldBe NotificationStatus.SENT
            }
        }
    }

    Given("일치하는 gateway 가 없는 채널의 Notification") {
        val notificationRepository = mockk<NotificationRepository>()
        val notificationCustomRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        val domainEventPublisher = mockk<DomainEventPublisher>()
        val discordGateway = fakeGateway(NotificationChannel.DISCORD, SendResult(success = true, errorMessage = null))

        val service = NotificationDomainService(
            notificationRepository = notificationRepository,
            notificationCustomRepository = notificationCustomRepository,
            channelGateways = listOf(discordGateway),
            templateRenderer = templateRenderer,
            domainEventPublisher = domainEventPublisher,
        )

        val notification = Notification.queue(
            userId = 1L,
            channel = NotificationChannel.SMS,
            templateId = "alert-triggered",
            payload = NotificationPayload(mapOf("_title" to "장애", "_body" to "본문")),
        )
        every { notificationRepository.findById(2L) } returns notification
        every { notificationRepository.save(any()) } returns notification

        When("dispatchById 를 호출하면") {
            service.dispatchById(2L)

            Then("DISCORD gateway 는 호출되지 않고 FAILED 로 전이된다") {
                notification.status shouldBe NotificationStatus.FAILED
            }
        }
    }

    Given("NotificationChannel 에 DISCORD 가 추가된 이후") {
        When("전체 채널 목록을 조회하면") {
            Then("기존 IN_APP/PUSH/EMAIL/SMS 값이 그대로 유지되고 DISCORD 가 추가된다") {
                NotificationChannel.entries.toSet() shouldContainExactlyInAnyOrder setOf(
                    NotificationChannel.IN_APP,
                    NotificationChannel.PUSH,
                    NotificationChannel.EMAIL,
                    NotificationChannel.SMS,
                    NotificationChannel.DISCORD,
                )
            }
        }
    }
})
