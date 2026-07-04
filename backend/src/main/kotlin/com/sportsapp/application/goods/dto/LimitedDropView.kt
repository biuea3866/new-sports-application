package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.entity.LimitedDrop
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import java.time.ZonedDateTime

/**
 * 한정판 회차 조회 응답. perUserLimit은 FE QuantityStepper 상한·"1인당 N개" 안내에 필요하다.
 * status는 영속 상태가 아니라 [LimitedDrop.effectiveStatus] 실시간 파생값이다(코드 리뷰 p2).
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
            status = drop.effectiveStatus(remaining),
            openAt = drop.openAt,
            closeAt = drop.closeAt,
            remaining = remaining,
            perUserLimit = drop.perUserLimit,
        )
    }
}
