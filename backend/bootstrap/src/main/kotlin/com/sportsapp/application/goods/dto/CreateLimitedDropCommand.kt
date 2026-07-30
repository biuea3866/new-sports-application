package com.sportsapp.application.goods.dto

import java.time.ZonedDateTime

/**
 * [com.sportsapp.application.goods.usecase.CreateLimitedDropUseCase] 실행 파라미터.
 */
data class CreateLimitedDropCommand(
    val productId: Long,
    val openAt: ZonedDateTime,
    val closeAt: ZonedDateTime,
    val limitedQuantity: Int,
    val perUserLimit: Int,
    val ownerUserId: Long,
)
