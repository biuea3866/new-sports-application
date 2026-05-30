package com.sportsapp.domain.goods

import java.math.BigDecimal

interface GoodsOrderCustomRepository {
    fun countConfirmedByProductOwnerUserId(ownerUserId: Long): Long
    fun sumRevenueByProductOwnerUserId(ownerUserId: Long): BigDecimal
}
