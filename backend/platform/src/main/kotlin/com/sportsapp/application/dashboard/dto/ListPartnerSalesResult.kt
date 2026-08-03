package com.sportsapp.application.dashboard.dto

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Page
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 파트너 매출 한 건 — **판매자 몫만** 싣는다.
 *
 * 결제 총액은 응답에 담지 않는다. 굿즈 주문에는 여러 판매자의 상품이 섞일 수 있어
 * `(결제 총액 - 내 매출)`로 **다른 판매자들의 매출 합계가 역산**되기 때문이다. 예약·티켓은
 * 판매자가 단일이라 결제 총액이 곧 [sellerAmount]와 같아, 총액을 따로 실어도 얻는 정보가 없다.
 *
 * [pgTransactionId]도 같은 이유로 **단일 판매자 주문(예약·티켓)에만** 싣는다 — 그 결제는
 * 온전히 이 판매자의 거래라 정산 대조에 쓸 수 있지만, 혼합 가능한 굿즈 주문의 PG 거래
 * 식별자는 남의 몫이 섞인 결제를 가리킨다.
 */
data class PartnerSaleResult(
    val paymentId: Long,
    val orderType: OrderType,
    val orderId: Long,
    val sellerAmount: BigDecimal,
    val method: String,
    val provider: String?,
    val status: PaymentStatus,
    val paidAt: ZonedDateTime?,
    val pgTransactionId: String?,
) {
    companion object {
        fun of(payment: Payment, sellerAmount: BigDecimal): PartnerSaleResult = PartnerSaleResult(
            paymentId = payment.id,
            orderType = payment.orderType,
            orderId = payment.orderId,
            sellerAmount = sellerAmount,
            method = payment.method.name,
            provider = payment.provider,
            status = payment.status,
            paidAt = payment.paidAt,
            // 혼합 가능한 주문 유형(굿즈)은 결제가 온전히 이 판매자 것이 아니다.
            pgTransactionId = payment.pgTransactionId.takeIf { payment.orderType.isSingleSellerOrder() },
        )
    }
}

data class ListPartnerSalesResult(
    val sales: List<PartnerSaleResult>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<PartnerSaleResult>): ListPartnerSalesResult = ListPartnerSalesResult(
            sales = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
