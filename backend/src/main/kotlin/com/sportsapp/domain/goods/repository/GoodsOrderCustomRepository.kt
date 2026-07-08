package com.sportsapp.domain.goods.repository

import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import java.math.BigDecimal
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable

interface GoodsOrderCustomRepository {
    fun countConfirmedByProductOwnerUserId(ownerUserId: Long): Long
    fun sumRevenueByProductOwnerUserId(ownerUserId: Long): BigDecimal
    fun sumRevenueByProductOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): BigDecimal

    /** order 통합조회(BE-08 예정)용 — 대표 상품명(title) 조인 읽기(BE-03). */
    fun findBy(userId: Long, pageable: Pageable): Page<GoodsOrderWithTitle>
}
