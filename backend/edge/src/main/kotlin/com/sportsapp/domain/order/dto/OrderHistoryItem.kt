package com.sportsapp.domain.order.dto

import com.sportsapp.domain.common.order.OrderType
import java.math.BigDecimal
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
 *
 * [S2-01] [com.sportsapp.domain.order.gateway.OrderHistoryGateway] 계약의 반환 타입이라 domain
 * 레이어로 옮겼다 — Gateway는 application을 참조할 수 없다.
 *
 * [amount]는 4개 주문 컨텍스트가 **각자 자기 데이터**로 채운 결제 금액이다(주문 내역에 금액이
 * 전혀 노출되지 않던 BE 계약 결함 후속) — edge/bootstrap이 payment 테이블을 역참조해 채우지
 * 않는다. 금액을 확정할 수 없는 경우(과거 BOOKING 예약 등 참조가 삭제된 경우)는 `null`로
 * 방어한다 — `0`(무료 확정값, RECRUITMENT)과 구분해야 하므로 0으로 방어하지 않는다.
 *
 * [seats]는 같은 title을 가진 서로 다른 TICKETING 주문을 사용자가 좌석으로 구분할 수 있게
 * 돕는 원본 필드 목록이다(내부 식별자 노출 금지 — sourceId 대신 사람이 읽는 좌석 정보).
 * 문자열로 미리 조합하지 않는다 — 모바일이 이미 티켓 구매 확인 화면에서 쓰는
 * `formatSeatDescription`("A석구역 1열 05번")과 서식이 어긋나는 두 번째 포맷터가 생기지
 * 않도록, 조합은 모바일 쪽 그 포맷터로 통일한다. 유형마다 다른 구분 정보를 억지로 한 필드에
 * 밀어넣지 않는다 — TICKETING 외 컨텍스트는 구분에 쓸 자기 데이터가 없어 `null`이다.
 */
data class OrderHistoryItem(
    val orderType: OrderType,
    val sourceId: Long,
    val title: String,
    val status: String,
    val paymentId: Long?,
    val detailPath: String,
    val createdAt: ZonedDateTime,
    val amount: BigDecimal?,
    val seats: List<OrderHistorySeat>? = null,
)

/**
 * [OrderHistoryItem.seats] 원소 — [com.sportsapp.domain.ticketing.dto.TicketSeatLabel]과 필드가
 * 1:1 대응한다. edge는 commerce를 컴파일 의존하지 않으므로(Gateway 존재 이유) 별도 타입으로
 * 정의한다 — LocalOrderHistoryAdapter가 매핑한다.
 */
data class OrderHistorySeat(
    val section: String,
    val rowNo: String,
    val seatNo: String,
)
