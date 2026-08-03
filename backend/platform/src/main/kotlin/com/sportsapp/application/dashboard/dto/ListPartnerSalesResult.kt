package com.sportsapp.application.dashboard.dto

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import org.springframework.data.domain.Page
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 파트너 매출 한 건 — 결제 정보 + **판매자 귀속 금액**.
 *
 * [amount]는 결제 총액이고 [sellerAmount]는 그중 이 판매자 몫이다. 굿즈 주문에는 여러
 * 판매자의 상품이 섞일 수 있어 결제 총액을 그대로 매출로 계상하면 남의 매출까지 내 것이 된다.
 * 예약·티켓은 판매자가 단일이라 두 값이 같다.
 */
data class PartnerSaleResult(
    val paymentId: Long,
    val orderType: OrderType,
    val orderId: Long,
    val amount: BigDecimal,
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
            amount = payment.amount,
            sellerAmount = sellerAmount,
            method = payment.method.name,
            provider = payment.provider,
            status = payment.status,
            paidAt = payment.paidAt,
            pgTransactionId = payment.pgTransactionId,
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
