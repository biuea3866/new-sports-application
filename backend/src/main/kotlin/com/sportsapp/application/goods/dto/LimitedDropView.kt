package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import java.time.ZonedDateTime

/**
 * 한정판 회차 조회 응답. perUserLimit은 FE QuantityStepper 상한·"1인당 N개" 안내에 필요하다.
 */
data class LimitedDropView(
    val dropId: Long,
    val productId: Long,
    val status: LimitedDropStatus,
    val openAt: ZonedDateTime,
    val closeAt: ZonedDateTime,
    val remaining: Int,
    val perUserLimit: Int,
) {
    companion object {
        fun of(drop: LimitedDrop, remaining: Int): LimitedDropView = LimitedDropView(
            dropId = drop.id,
            productId = drop.productId,
            status = drop.currentStatus,
            openAt = drop.openAt,
            closeAt = drop.closeAt,
            remaining = remaining,
            perUserLimit = drop.perUserLimit,
        )
    }
}
