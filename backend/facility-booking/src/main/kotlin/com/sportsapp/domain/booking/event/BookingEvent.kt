package com.sportsapp.domain.booking.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sportsapp.domain.common.AbstractDomainEvent
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 예약(booking) 서브 도메인의 생명주기 사건을 담는 단일 도메인 이벤트.
 *
 * 토픽 네이밍 규칙(event.{domain}.{sub-domain}.v{N})에 따라 booking 은 단일 집계이므로
 * 모든 예약 생명주기 이벤트를 하나의 토픽 [TOPIC] 으로 발행한다. 확정 같은 사건 종류는
 * 토픽이 아니라 payload 의 판별자([eventType])로만 구분한다.
 *
 * 라우팅 key 는 서브 도메인 엔티티 PK(bookingId = aggregateId)로, 같은 예약의 이벤트가
 * 한 파티션에 모여 순서가 보장된다.
 *
 * Kafka Producer 는 타입 헤더를 붙이지 않으므로(ADD_TYPE_INFO_HEADERS=false), payload 안에
 * 판별 프로퍼티 [eventType] 을 두어 다형 역직렬화한다. [aggregateId]/[topic] 은 직렬화되지만
 * 역직렬화 시 생성자 인자가 아니므로 무시한다([JsonIgnoreProperties]).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
    property = "eventType",
    visible = true,
)
@JsonSubTypes(
    JsonSubTypes.Type(value = BookingEvent.Confirmed::class, name = BookingEvent.TYPE_CONFIRMED),
)
sealed class BookingEvent(
    val bookingId: Long,
    val paymentId: Long,
    val recipientUserId: Long,
    eventId: String,
    occurredAt: ZonedDateTime,
) : AbstractDomainEvent(
    aggregateId = bookingId,
    topic = TOPIC,
    eventId = eventId,
    occurredAt = occurredAt,
) {
    abstract val eventType: String

    /**
     * 예약이 확정되었다는 과거형 사실. 알림 컨텍스트가 별도 조회 없이 확정 알림을 만들 수 있도록
     * 수신자([recipientUserId])를 payload 에 함께 담는다.
     */
    class Confirmed(
        bookingId: Long,
        paymentId: Long,
        recipientUserId: Long,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : BookingEvent(bookingId, paymentId, recipientUserId, eventId, occurredAt) {
        override val eventType: String = TYPE_CONFIRMED
    }

    companion object {
        const val TOPIC = "event.booking.booking.v1"
        const val TYPE_CONFIRMED = "CONFIRMED"
    }
}
