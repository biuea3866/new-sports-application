package com.sportsapp.domain.notification

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.exception.UnknownTemplateException
import com.sportsapp.domain.notification.gateway.TemplateRenderer
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationCategory
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.notification.vo.RenderedNotification
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 알림함(GET /notifications/me) 목록이 사용자에게 보여줄 제목·본문·분류·읽음 여부를 갖추는지 검증한다.
 *
 * 회귀 배경: 목록 응답이 templateId/channel/status 만 담아 앱 알림함 16건이 전부 빈 회색 막대로
 * 렌더됐다(유즈케이스 캡쳐 36-알림함). 제목·본문은 payload 에 렌더 결과(_title/_body)가 있으면
 * 그것을, 없으면 TemplateRenderer 로 렌더해 채운다. 알 수 없는 템플릿 1건이 목록 전체를 실패시키면
 * 안 되므로 기본 문구로 방어한다.
 */
class NotificationDomainServiceListViewTest : BehaviorSpec({

    fun serviceWith(
        customRepository: NotificationCustomRepository,
        templateRenderer: TemplateRenderer,
    ) = NotificationDomainService(
        notificationRepository = mockk<NotificationRepository>(),
        notificationCustomRepository = customRepository,
        channelGateways = emptyList(),
        templateRenderer = templateRenderer,
        domainEventPublisher = mockk<DomainEventPublisher>(),
    )

    /**
     * createdAt 은 JPA auditing 이 영속화 시점에 채우는 lateinit 필드라 단위 테스트에서는
     * 비어 있다. 조회 결과 매핑만 검증하면 되므로 감사 필드만 고정값으로 스텁한다.
     */
    val persistedAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC)

    fun queuedSpy(notification: Notification): Notification = spyk(notification) {
        every { createdAt } returns persistedAt
    }

    Given("payload 에 렌더 결과(_title/_body)가 이미 저장된 결제 알림이 있는 상황") {
        val notification = queuedSpy(Notification.queue(
            userId = 7L,
            channel = NotificationChannel.IN_APP,
            templateId = "payment-completed",
            payload = NotificationPayload(
                mapOf("_title" to "결제 완료", "_body" to "88,000원 결제가 완료되었습니다."),
            ),
        ))
        val customRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        every { customRepository.findByUserIdPaged(7L, false, any()) } returns
            PageImpl(listOf(notification), PageRequest.of(0, 20), 1)

        When("알림함 목록을 조회하면") {
            val views = serviceWith(customRepository, templateRenderer)
                .listMyNotificationViews(userId = 7L, onlyUnread = false, page = 0, size = 20)

            Then("저장된 제목·본문이 그대로 노출된다") {
                views.content.single().title shouldBe "결제 완료"
                views.content.single().content shouldBe "88,000원 결제가 완료되었습니다."
            }

            Then("templateId 로부터 결제 분류가 결정된다") {
                views.content.single().category shouldBe NotificationCategory.PAYMENT
            }

            Then("readAt 이 없으므로 안읽음으로 표시된다") {
                views.content.single().isRead shouldBe false
            }
        }
    }

    Given("렌더 결과가 payload 에 없는 예약 알림이 있는 상황") {
        val notification = queuedSpy(Notification.queue(
            userId = 7L,
            channel = NotificationChannel.IN_APP,
            templateId = "booking-confirmed",
            payload = NotificationPayload(mapOf("facilityName" to "잠실 실내체육관")),
        ))
        val customRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        every { customRepository.findByUserIdPaged(7L, false, any()) } returns
            PageImpl(listOf(notification), PageRequest.of(0, 20), 1)
        every { templateRenderer.render("booking-confirmed", any()) } returns
            RenderedNotification(title = "예약 확정", body = "잠실 실내체육관 예약이 확정되었습니다.")

        When("알림함 목록을 조회하면") {
            val views = serviceWith(customRepository, templateRenderer)
                .listMyNotificationViews(userId = 7L, onlyUnread = false, page = 0, size = 20)

            Then("템플릿을 렌더해 제목·본문을 채운다") {
                views.content.single().title shouldBe "예약 확정"
                views.content.single().content shouldBe "잠실 실내체육관 예약이 확정되었습니다."
            }

            Then("예약 분류로 결정된다") {
                views.content.single().category shouldBe NotificationCategory.BOOKING
            }
        }
    }

    Given("설정에 없는 템플릿으로 적재된 알림이 있는 상황") {
        val notification = queuedSpy(Notification.queue(
            userId = 7L,
            channel = NotificationChannel.IN_APP,
            templateId = "ticket-issued",
            payload = NotificationPayload(emptyMap()),
        ))
        val customRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        every { customRepository.findByUserIdPaged(7L, false, any()) } returns
            PageImpl(listOf(notification), PageRequest.of(0, 20), 1)
        every { templateRenderer.render("ticket-issued", any()) } throws
            UnknownTemplateException("ticket-issued")

        When("알림함 목록을 조회하면") {
            val views = serviceWith(customRepository, templateRenderer)
                .listMyNotificationViews(userId = 7L, onlyUnread = false, page = 0, size = 20)

            Then("목록 전체가 실패하지 않고 기본 제목으로 방어된다") {
                views.content.single().title shouldBe NotificationDomainService.FALLBACK_TITLE
                views.content.single().content shouldBe ""
            }

            Then("티켓 발권은 이벤트 분류로 결정된다") {
                views.content.single().category shouldBe NotificationCategory.EVENT
            }
        }
    }

    Given("이미 읽은 알림이 있는 상황") {
        val notification = queuedSpy(Notification.queue(
            userId = 7L,
            channel = NotificationChannel.IN_APP,
            templateId = "welcome",
            payload = NotificationPayload(mapOf("_title" to "가입 완료", "_body" to "환영합니다.")),
        )).also { it.markRead() }
        val customRepository = mockk<NotificationCustomRepository>()
        val templateRenderer = mockk<TemplateRenderer>()
        every { customRepository.findByUserIdPaged(7L, true, any()) } returns
            PageImpl(listOf(notification), PageRequest.of(0, 20), 1)

        When("알림함 목록을 조회하면") {
            val views = serviceWith(customRepository, templateRenderer)
                .listMyNotificationViews(userId = 7L, onlyUnread = true, page = 0, size = 20)

            Then("읽음으로 표시되고 분류는 시스템이다") {
                views.content.single().isRead shouldBe true
                views.content.single().category shouldBe NotificationCategory.SYSTEM
            }
        }
    }
})
