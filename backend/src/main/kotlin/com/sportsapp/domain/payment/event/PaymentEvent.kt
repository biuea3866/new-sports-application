package com.sportsapp.domain.payment.event

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sportsapp.domain.common.AbstractDomainEvent
import com.sportsapp.domain.payment.vo.OrderType
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

/**
 * 결제 서브 도메인의 생명주기 사건을 담는 단일 도메인 이벤트.
 *
 * 토픽 네이밍 규칙(event.{domain}.{sub-domain}.v{N})에 따라 payment 는 단일 집계이므로
 * 모든 결제 생명주기 이벤트를 하나의 토픽 [TOPIC] 으로 발행한다. 확정/취소 같은 사건 종류는
 * 토픽이 아니라 payload 의 판별자([eventType])로만 구분한다.
 *
 * 라우팅 key 는 서브 도메인 엔티티 PK(paymentId = aggregateId)로, 같은 결제의 이벤트가
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
    JsonSubTypes.Type(value = PaymentEvent.Confirmed::class, name = PaymentEvent.TYPE_CONFIRMED),
    JsonSubTypes.Type(value = PaymentEvent.Cancelled::class, name = PaymentEvent.TYPE_CANCELLED),
)
sealed class PaymentEvent(
    val paymentId: Long,
    val orderType: OrderType,
    val orderId: Long,
    eventId: String,
    occurredAt: ZonedDateTime,
) : AbstractDomainEvent(
    aggregateId = paymentId,
    topic = TOPIC,
    eventId = eventId,
    occurredAt = occurredAt,
) {
    abstract val eventType: String

    /**
     * 결제가 확정(승인)되었다는 과거형 사실. 각 주문 컨텍스트가 자기 주문을 확정한다.
     *
     * 알림 컨텍스트가 별도 조회 없이 결제 완료 알림을 만들 수 있도록 수신자([recipientUserId])와
     * 결제 금액([amount])을 payload 에 함께 담는다.
     */
    class Confirmed(
        paymentId: Long,
        orderType: OrderType,
        orderId: Long,
        val recipientUserId: Long,
        val amount: Long,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : PaymentEvent(paymentId, orderType, orderId, eventId, occurredAt) {
        override val eventType: String = TYPE_CONFIRMED
    }

    /** 결제가 취소되었다는 과거형 사실. 각 주문 컨텍스트가 자기 주문을 취소(PENDING 정리)한다. */
    class Cancelled(
        paymentId: Long,
        orderType: OrderType,
        orderId: Long,
        eventId: String = UUID.randomUUID().toString(),
        occurredAt: ZonedDateTime = ZonedDateTime.now(ZoneOffset.UTC),
    ) : PaymentEvent(paymentId, orderType, orderId, eventId, occurredAt) {
        override val eventType: String = TYPE_CANCELLED
    }

    companion object {
        const val TOPIC = "event.payment.payment.v1"
        const val TYPE_CONFIRMED = "CONFIRMED"
        const val TYPE_CANCELLED = "CANCELLED"
    }
}
