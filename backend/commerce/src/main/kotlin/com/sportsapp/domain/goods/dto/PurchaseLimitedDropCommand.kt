package com.sportsapp.domain.goods.dto

/**
 * [com.sportsapp.domain.goods.service.LimitedDropDomainService.purchase] 실행 파라미터.
 */
data class PurchaseLimitedDropCommand(
    val dropId: Long,
    val userId: Long,
    val quantity: Int,
    val idempotencyKey: String,
)
