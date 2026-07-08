package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.application.goods.dto.CreateLimitedDropCommand
import java.time.ZonedDateTime

/**
 * 판매자 한정판 회차 개설 요청(`POST /limited-drops`). ownerUserId는 `X-User-Id` 헤더에서 받는다.
 */
data class CreateLimitedDropRequest(
    val productId: Long,
    val openAt: ZonedDateTime,
    val closeAt: ZonedDateTime,
    val limitedQuantity: Int,
    val perUserLimit: Int,
) {
    fun toCommand(ownerUserId: Long): CreateLimitedDropCommand = CreateLimitedDropCommand(
        productId = productId,
        openAt = openAt,
        closeAt = closeAt,
        limitedQuantity = limitedQuantity,
        perUserLimit = perUserLimit,
        ownerUserId = ownerUserId,
    )
}
