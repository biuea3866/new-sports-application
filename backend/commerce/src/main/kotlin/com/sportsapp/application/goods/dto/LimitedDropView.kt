package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 한정판 회차 조회 응답. perUserLimit은 FE QuantityStepper 상한·"1인당 N개" 안내에 필요하다.
 * status는 영속 상태가 아니라 [LimitedDrop.effectiveStatus] 실시간 파생값이다(코드 리뷰 p2).
 * totalQuantity(=limitedQuantity)·price는 FE 재고비율 바·결제 amount 전달에 필요하다.
 * productName·productImageUrl 은 상세 화면이 productId 대신 사람이 읽는 이름을 노출하기 위해 필요하다.
 */
data class LimitedDropView(
    val dropId: Long,
    val productId: Long,
    val productName: String,
    val productImageUrl: String,
    val status: LimitedDropStatus,
    val openAt: ZonedDateTime,
    val closeAt: ZonedDateTime,
    val remaining: Int,
    val perUserLimit: Int,
    val totalQuantity: Int,
    val price: BigDecimal,
) {
    companion object {
        fun of(
            drop: LimitedDrop,
            remaining: Int,
            productName: String,
            productImageUrl: String,
            price: BigDecimal,
        ): LimitedDropView = LimitedDropView(
            dropId = drop.id,
            productId = drop.productId,
            productName = productName,
            productImageUrl = productImageUrl,
            status = drop.effectiveStatus(remaining),
            openAt = drop.openAt,
            closeAt = drop.closeAt,
            remaining = remaining,
            perUserLimit = drop.perUserLimit,
            totalQuantity = drop.limitedQuantity,
            price = price,
        )
    }
}
