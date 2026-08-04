package com.sportsapp.infrastructure.order

import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.order.dto.OrderHistoryItem
import com.sportsapp.domain.order.dto.OrderHistorySeat
import com.sportsapp.domain.order.gateway.OrderHistoryGateway
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSeatLabel
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component

/**
 * 1단계 로컬 어댑터 — edge의 통합 주문내역 계약을 같은 프로세스의 4개 코어 DomainService 호출로
 * 만족시킨다 (S2-01, Branch By Abstraction).
 *
 * **조립자(`bootstrap`)에 두는 이유**: edge는 commerce·facility-booking·social을 의존하지 않아야
 * 하고, 그 모듈들도 edge를 의존하지 않는다. 두 쪽을 모두 아는 유일한 지점이 컴포지션 루트다.
 * 2단계에는 이 어댑터를 버리고 edge 안의 RestClient 구현이 각 서비스의 원격 주문내역 조회
 * 엔드포인트를 호출한다(§9 Branch By Abstraction — 계약은 그대로, 구현만 교체).
 *
 * 타 모듈 DTO(`BookingOrderItem`·`GoodsOrderWithTitle`·`TicketOrderWithEventTitle`·
 * `ApplicationWithRecruitmentTitle`) → [OrderHistoryItem] 매핑은 이동 전 `OrderCompositionService`가
 * 갖던 것을 그대로 옮겼다 — 도메인별 상세 경로·title 매핑 규칙에 동작 변화가 없다.
 *
 * `@Profile` 미부착 — 이동 전 `OrderCompositionService`가 프로파일 무관 빈이었고, 여기서 주입받는
 * 4개 DomainService는 test-jpa 프로파일에서도(facility Mongo 제외) 그대로 해석된다.
 */
@Component
class LocalOrderHistoryAdapter(
    private val bookingDomainService: BookingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val ticketingDomainService: TicketingDomainService,
    private val recruitmentDomainService: RecruitmentDomainService,
) : OrderHistoryGateway {

    override fun findBookingOrders(userId: Long): List<OrderHistoryItem> =
        bookingDomainService.findOrderHistory(userId).map { it.toOrderHistoryItem() }

    override fun findGoodsOrders(userId: Long, pageable: Pageable): List<OrderHistoryItem> =
        goodsDomainService.listMyOrdersWithTitle(userId, pageable).content.map { it.toOrderHistoryItem() }

    override fun findTicketingOrders(userId: Long): List<OrderHistoryItem> =
        ticketingDomainService.listTicketOrdersBy(userId).map { it.toOrderHistoryItem() }

    override fun findRecruitmentOrders(userId: Long): List<OrderHistoryItem> =
        recruitmentDomainService.listApplicationsWithTitleBy(userId).map { it.toOrderHistoryItem() }
}

private fun BookingOrderItem.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.BOOKING,
    sourceId = bookingId,
    title = title,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/bookings/$bookingId",
    createdAt = createdAt,
    amount = amount,
)

private fun GoodsOrderWithTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.GOODS,
    sourceId = order.id,
    title = title,
    status = order.status.name,
    paymentId = order.paymentId,
    detailPath = "/goods-orders/${order.id}",
    createdAt = order.createdAt,
    amount = order.totalAmount,
)

private fun TicketOrderWithEventTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.TICKETING,
    sourceId = ticketOrderId,
    title = eventTitle,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/ticket-orders/$ticketOrderId",
    createdAt = createdAt,
    amount = totalAmount,
    // 같은 이벤트에 여러 좌석 주문이 있을 때 title(이벤트명)만으로는 구분되지 않는다 —
    // 내부 식별자(sourceId) 대신 좌석 원본 필드로 구분한다(조합은 모바일 포맷터가 담당).
    seats = seats.map { it.toOrderHistorySeat() }.takeIf { it.isNotEmpty() },
)

private fun TicketSeatLabel.toOrderHistorySeat(): OrderHistorySeat =
    OrderHistorySeat(section = section, rowNo = rowNo, seatNo = seatNo)

private fun ApplicationWithRecruitmentTitle.toOrderHistoryItem(): OrderHistoryItem = OrderHistoryItem(
    orderType = OrderType.RECRUITMENT,
    sourceId = applicationId,
    title = recruitmentTitle,
    status = status.name,
    paymentId = paymentId,
    detailPath = "/applications/$applicationId",
    createdAt = createdAt,
    amount = feeAmount,
)
