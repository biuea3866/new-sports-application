package com.sportsapp.presentation.notification.worker

import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.application.notification.usecase.EnqueueNotificationUseCase
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.payment.event.PaymentEvent
import com.sportsapp.domain.ticketing.event.TicketEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 결제/예약/티켓 서브 도메인 이벤트 토픽을 각각 구독해 사용자 알림(IN_APP·PUSH)을 발행한다.
 *
 * 각 토픽은 여러 컨텍스트가 팬아웃 구독하므로 알림 컨텍스트 고유 groupId 로 그룹을 분리한다.
 * 사건 종류(Confirmed/Issued 등)는 토픽이 아니라 payload 의 sealed 변이로 구분하며,
 * 알림 대상이 아닌 변이(Cancelled 등)는 no-op 으로 흘려보낸다.
 * 도메인 이벤트를 알림 command 로 옮기는 매핑은 domain→notification 결합을 피하기 위해
 * 워커 내부 private 헬퍼로만 수행한다. 멱등은 하류 UseCase 가 eventId 로 보장한다.
 */
@Component
class NotificationEventWorker(
    private val enqueueNotificationUseCase: EnqueueNotificationUseCase,
) {
    @KafkaListener(topics = [PaymentEvent.TOPIC], groupId = "notification-payment")
    fun consumePayment(event: PaymentEvent) {
        when (event) {
            is PaymentEvent.Confirmed -> enqueueBoth(
                templateId = "payment-completed",
                recipientUserId = event.recipientUserId,
                baseEventId = event.eventId,
                payload = NotificationPayload(mapOf("amount" to event.amount.toString())),
            )
            is PaymentEvent.Cancelled -> Unit
        }
    }

    @KafkaListener(topics = [BookingEvent.TOPIC], groupId = "notification-booking")
    fun consumeBooking(event: BookingEvent) {
        when (event) {
            is BookingEvent.Confirmed -> enqueueBoth(
                templateId = "booking-confirmed",
                recipientUserId = event.recipientUserId,
                baseEventId = event.eventId,
                payload = NotificationPayload(emptyMap()),
            )
        }
    }

    @KafkaListener(topics = [TicketEvent.TOPIC], groupId = "notification-ticketing")
    fun consumeTicket(event: TicketEvent) {
        when (event) {
            is TicketEvent.Issued -> enqueueBoth(
                templateId = "ticket-issued",
                recipientUserId = event.recipientUserId,
                baseEventId = event.eventId,
                payload = NotificationPayload(mapOf("eventTitle" to event.eventTitle)),
            )
        }
    }

    private fun enqueueBoth(
        templateId: String,
        recipientUserId: Long,
        baseEventId: String,
        payload: NotificationPayload,
    ) {
        enqueueNotificationUseCase.execute(
            EnqueueNotificationCommand(
                channel = NotificationChannel.IN_APP,
                templateId = templateId,
                payload = payload,
                recipientUserId = recipientUserId,
                eventId = baseEventId,
            )
        )
        enqueueNotificationUseCase.execute(
            EnqueueNotificationCommand(
                channel = NotificationChannel.PUSH,
                templateId = templateId,
                payload = payload,
                recipientUserId = recipientUserId,
                eventId = "$baseEventId:push",
            )
        )
    }
}
