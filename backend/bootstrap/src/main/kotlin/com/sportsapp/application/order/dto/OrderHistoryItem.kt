package com.sportsapp.application.order.dto

import com.sportsapp.domain.common.order.OrderType
import java.time.ZonedDateTime

/**
 * 통합 주문내역(BE-08) 단일 항목 — 4개 주문 도메인을 정규화한 응답 형태.
 *
 * [sourceId]는 원본 도메인(주문이 속한 그 도메인) 자신의 PK다 — GoodsOrder.id·TicketOrder.id·
 * Application.id·Booking.id. 참조 엔티티(Product·Event·Recruitment·Facility) PK가 아니다.
 * Facility.id가 String(불투명 외부 코드)이라 참조 엔티티 PK로는 `Long` 타입을 통일할 수 없기
 * 때문이며(TDD 인터페이스 시그니처 `sourceId: Long`), 4개 도메인 모두 자기 주문 레코드의 PK는
 * Long이라 타입이 일관된다.
 *
 * [detailPath]도 같은 이유로 그 주문 자신의 상세 경로다 — GOODS=`/goods-orders/{id}`,
 * TICKETING=`/ticket-orders/{id}`, BOOKING=`/bookings/{id}`, RECRUITMENT=`/applications/{id}`
 * (RECRUITMENT는 현재 단건 조회 GET 엔드포인트가 없어 후속 티켓 과제로 남는다 — 완료 보고 참조).
 *
 * [title]은 각 주문 컨텍스트가 자기 데이터로 구성해 반환한 값을 그대로 매핑한 것이다
 * (파사드는 이름을 만들지 않는다).
 */
data class OrderHistoryItem(
    val orderType: OrderType,
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val detailPath: String,
    val createdAt: ZonedDateTime,
)
