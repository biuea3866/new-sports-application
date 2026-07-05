package com.sportsapp.presentation.goods.dto.response

import com.sportsapp.application.goods.dto.LimitedDropView
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 한정판 회차 응답(`POST /limited-drops`, `GET /limited-drops/{dropId}` 공용).
 * perUserLimit은 FE QuantityStepper 상한·"1인당 N개" 안내에, totalQuantity·price는
 * FE 재고비율 바·결제 amount 전달에 필요하다.
 */
data class LimitedDropResponse(
    val dropId: Long,
    val productId: Long,
    val status: LimitedDropStatus,
    val openAt: ZonedDateTime,
    val closeAt: ZonedDateTime,
    val remaining: Int,
    val perUserLimit: Int,
    val totalQuantity: Int,
    val price: BigDecimal,
) {
    companion object {
        fun of(view: LimitedDropView): LimitedDropResponse = LimitedDropResponse(
            dropId = view.dropId,
            productId = view.productId,
            status = view.status,
            openAt = view.openAt,
            closeAt = view.closeAt,
            remaining = view.remaining,
            perUserLimit = view.perUserLimit,
            totalQuantity = view.totalQuantity,
            price = view.price,
        )
    }
}
