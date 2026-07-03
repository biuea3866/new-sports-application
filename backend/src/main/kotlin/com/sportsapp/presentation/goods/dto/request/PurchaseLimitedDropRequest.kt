package com.sportsapp.presentation.goods.dto.request

import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand

/**
 * 한정판 구매 요청(`POST /limited-drops/{dropId}/orders`).
 * dropId는 경로 변수, userId는 `X-User-Id`, idempotencyKey는 `Idempotency-Key` 헤더에서 받는다.
 */
data class PurchaseLimitedDropRequest(
    val quantity: Int,
) {
    fun toCommand(dropId: Long, userId: Long, idempotencyKey: String): PurchaseLimitedDropCommand =
        PurchaseLimitedDropCommand(
            dropId = dropId,
            userId = userId,
            quantity = quantity,
            idempotencyKey = idempotencyKey,
        )
}
