package com.sportsapp.application.catalog.dto

import com.sportsapp.domain.goods.vo.SellerType
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * catalog 통합검색 응답 항목 (BE-07). 5개 판매 대상 도메인의 서로 다른 Entity를 단일 shape로
 * 정규화한다 — 가격 없는 유형(TICKET)은 [price]가 null, sellerType이 없는 도메인은
 * [sellerType]이 null이다.
 */
data class CatalogItem(
    val itemType: CatalogItemType,
    val sourceId: Long,
    val title: String,
    val price: BigDecimal?,
    val sellerType: SellerType?,
    val status: String,
    val detailPath: String,
    val createdAt: ZonedDateTime,
)
