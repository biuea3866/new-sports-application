package com.sportsapp.application.goods

import java.time.ZonedDateTime

data class GetGoodsSalesCommand(
    val operatorUserId: Long,
    val productId: Long?,
    val from: ZonedDateTime,
    val to: ZonedDateTime,
)
