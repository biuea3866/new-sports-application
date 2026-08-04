package com.sportsapp.domain.catalog.dto

import com.sportsapp.domain.catalog.vo.SellerType
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * catalog 통합검색 응답 항목 (BE-07). 5개 판매 대상 도메인의 서로 다른 Entity를 단일 shape로
 * 정규화한다 — 가격 없는 유형(TICKET)은 [price]가 null, sellerType이 없는 도메인은
 * [sellerType]이 null이다.
 *
 * [locationName]·[scheduledAt]은 같은 제목의 서로 다른 항목(예: 시설 4곳의 동일명 프로그램)을
 * 화면에서 구분하기 위한 부가 표시 정보다 — 유형마다 의미 있는 값이 없으면 null이며, 유형명을
 * 반복하거나 빈 문자열로 채우지 않는다:
 * - [locationName]: PROGRAM은 시설명, TICKET은 경기장명. 그 외 유형은 null.
 * - [scheduledAt]: TICKET은 경기 시작 일시, RECRUITMENT는 모임 활동 일시. 그 외 유형은 null.
 * 내부 식별자([sourceId] 등)는 구분 정보로 노출하지 않는다.
 *
 * [S2-01] [com.sportsapp.domain.catalog.gateway.CatalogSearchGateway]의 반환 타입이라 domain
 * 레이어로 옮겼다 — 타 모듈 Entity(ProductWithStock 등) → 이 타입으로의 매핑은 조립자
 * (`bootstrap`) 로컬 어댑터가 수행하고, edge는 이 타입만 안다.
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
    val locationName: String?,
    val scheduledAt: ZonedDateTime?,
)
