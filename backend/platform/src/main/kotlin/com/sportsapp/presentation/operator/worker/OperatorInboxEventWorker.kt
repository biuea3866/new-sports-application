package com.sportsapp.presentation.operator.worker

import com.sportsapp.application.operator.dto.RecordOperatorInboxEventCommand
import com.sportsapp.application.operator.usecase.RecordOperatorInboxEventUseCase
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import com.sportsapp.domain.ticketing.event.TicketEvent
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.stereotype.Component

/**
 * 파트너 운영 인박스 적재 — 예약·티켓 서브 도메인 이벤트를 구독해 운영 사건을 쌓는다.
 *
 * 기존에는 `OperatorInboxNotificationDomainService.create()` 호출부가 하나도 없어 운영 인박스가
 * 영구히 빈 화면이었다(읽기·읽음처리 경로만 존재).
 *
 * 개인 알림([com.sportsapp.presentation.notification.worker.NotificationEventWorker])과 목적이
 * 다르다 — 이쪽은 파트너가 **운영 조치를 취할 대상**을 모으는 피드라 상세로 이동할 링크를 담는다.
 *
 * 각 토픽은 여러 컨텍스트가 팬아웃 구독하므로 운영 인박스 고유 groupId 로 그룹을 분리한다.
 * Kafka 는 at-least-once 이므로 멱등은 하류 UseCase 가 eventId 로 보장한다.
 */
@Component
class OperatorInboxEventWorker(
    private val recordOperatorInboxEventUseCase: RecordOperatorInboxEventUseCase,
) {
    @KafkaListener(
        topics = [BookingEvent.TOPIC],
        groupId = "operator-inbox-booking",
        containerFactory = "bookingEventKafkaListenerContainerFactory",
    )
    fun consumeBooking(event: BookingEvent) {
        when (event) {
            is BookingEvent.Confirmed -> recordForOwner(
                ownerUserId = event.facilityOwnerUserId,
                eventId = event.eventId,
                type = OperatorInboxNotificationType.BOOKING_RECEIVED,
                title = "신규 예약 접수",
                body = "내 시설에 예약이 접수됐습니다. 예약 번호 ${event.bookingId}",
                link = "/portal/bookings",
            )
        }
    }

    @KafkaListener(
        topics = [TicketEvent.TOPIC],
        groupId = "operator-inbox-ticketing",
        containerFactory = "ticketEventKafkaListenerContainerFactory",
    )
    fun consumeTicket(event: TicketEvent) {
        when (event) {
            is TicketEvent.Issued -> recordForOwner(
                ownerUserId = event.eventOwnerUserId,
                eventId = event.eventId,
                type = OperatorInboxNotificationType.TICKET_SOLD,
                title = "티켓 판매",
                body = "${event.eventTitle} 티켓이 발권됐습니다.",
                link = "/portal/events",
            )
        }
    }

    /** 소유주를 모르는 이벤트는 적재 대상이 없으므로 흘려보낸다. */
    private fun recordForOwner(
        ownerUserId: Long?,
        eventId: String,
        type: OperatorInboxNotificationType,
        title: String,
        body: String,
        link: String,
    ) {
        if (ownerUserId == null) return
        recordOperatorInboxEventUseCase.execute(
            RecordOperatorInboxEventCommand(
                eventId = eventId,
                recipientUserId = ownerUserId,
                type = type,
                title = title,
                body = body,
                link = link,
            )
        )
    }
}
