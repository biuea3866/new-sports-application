package com.sportsapp.domain.goods

import java.math.BigDecimal
import java.time.ZonedDateTime

interface GoodsOrderCustomRepository {
    fun countConfirmedByProductOwnerUserId(ownerUserId: Long): Long
    fun sumRevenueByProductOwnerUserId(ownerUserId: Long): BigDecimal
    fun sumRevenueByProductOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): BigDecimal
}
