package com.sportsapp.application.dashboard.usecase

import com.sportsapp.application.dashboard.dto.ListPartnerSalesCommand
import com.sportsapp.application.dashboard.dto.ListPartnerSalesResult
import com.sportsapp.application.dashboard.dto.PartnerSaleResult
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderRef
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

/**
 * 포털 "매출·결제 내역" — 파트너가 **판매한** 건의 결제를 모아 보여준다.
 *
 * payment는 판매자를 알지 못하고, 알게 만들면 공용 컨텍스트가 주문 컨텍스트를 역참조하게 된다.
 * 그래서 "내 주문이 무엇인가"는 각 주문 컨텍스트가 답하고, 두 컨텍스트를 아는 유일한 레이어인
 * application이 그 참조를 모아 payment에 결제를 되묻는다.
 *
 * **규모 주의**: 주문 참조를 모아 `IN`으로 조회하므로 참조 수가 주문 수에 비례한다. 현재 규모
 * (파트너당 상품 6·경기 2·슬롯 81)에서는 문제없으나, 주문이 수만 건 단위가 되면 판매자 축을
 * 별도로 색인하는 방식으로 재검토가 필요하다.
 */
@Service
class ListPartnerSalesUseCase(
    private val bookingDomainService: BookingDomainService,
    private val ticketingDomainService: TicketingDomainService,
    private val goodsDomainService: GoodsDomainService,
    private val paymentDomainService: PaymentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: ListPartnerSalesCommand): ListPartnerSalesResult {
        val goodsSellerAmounts = goodsDomainService.findGoodsOrderSellerAmounts(command.ownerUserId)
        val orderRefs = collectOrderRefs(command.ownerUserId, goodsSellerAmounts.keys)
        if (orderRefs.isEmpty()) return ListPartnerSalesResult.of(Page.empty(command.pageable))
        val payments = paymentDomainService.findSalesByOrderRefs(
            orderRefs = orderRefs,
            status = command.status,
            paidAtFrom = command.paidAtFrom,
            paidAtTo = command.paidAtTo,
            pageable = command.pageable,
        )
        return ListPartnerSalesResult.of(
            payments.map { payment ->
                PartnerSaleResult.of(payment, sellerAmountOf(payment.orderType, payment.orderId, payment.amount, goodsSellerAmounts))
            }
        )
    }

    /** 세 주문 컨텍스트가 각자 답한 "내 주문"을 유형과 함께 모은다. */
    private fun collectOrderRefs(ownerUserId: Long, goodsOrderIds: Set<Long>): List<OrderRef> {
        val bookingRefs = bookingDomainService.findBookingIdsForFacilityOwner(ownerUserId)
            .map { OrderRef(OrderType.BOOKING, it) }
        val ticketingRefs = ticketingDomainService.findTicketOrderIdsForEventOwner(ownerUserId)
            .map { OrderRef(OrderType.TICKETING, it) }
        val goodsRefs = goodsOrderIds.map { OrderRef(OrderType.GOODS, it) }
        return bookingRefs + ticketingRefs + goodsRefs
    }

    /**
     * 판매자 귀속 금액 — 굿즈만 결제 총액과 다를 수 있다(한 주문에 여러 판매자 상품).
     * 예약·티켓은 주문이 시설/경기 하나에 귀속돼 판매자가 단일이므로 결제 총액이 곧 내 매출이다.
     */
    private fun sellerAmountOf(
        orderType: OrderType,
        orderId: Long,
        paymentAmount: BigDecimal,
        goodsSellerAmounts: Map<Long, BigDecimal>,
    ): BigDecimal =
        if (orderType == OrderType.GOODS) goodsSellerAmounts[orderId] ?: BigDecimal.ZERO else paymentAmount
}
