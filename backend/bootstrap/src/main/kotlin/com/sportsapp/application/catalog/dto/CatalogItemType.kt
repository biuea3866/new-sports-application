package com.sportsapp.application.catalog.dto

/**
 * catalog 통합검색이 조합하는 판매 대상 유형 (BE-07).
 *
 * PRODUCT/LIMITED_DROP은 같은 goods 도메인 조회(`GoodsDomainService.search`) 결과에서
 * `ProductWithStock.limitedDropId` 존재 여부로 갈린다 — 별도 조회 호출을 두지 않는다.
 */
enum class CatalogItemType {
    PRODUCT,
    LIMITED_DROP,
    TICKET,
    PROGRAM,
    RECRUITMENT,
}
