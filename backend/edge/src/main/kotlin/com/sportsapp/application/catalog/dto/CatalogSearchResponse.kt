package com.sportsapp.application.catalog.dto

/**
 * catalog 통합검색 응답 (BE-07). [failedDomains]는 300ms 타임아웃/조회 실패로 결과에서
 * 제외된 판매 대상 유형을 담는다(FR-11) — 비어 있으면 전체가 정상 조회됐다는 뜻이다.
 *
 * FE 계약(`mobile/api/catalog-types.ts`)이 `failedDomains: CatalogItemType[]`를 기대하고,
 * `mobile/lib/catalog-format.ts`의 `CATALOG_ITEM_TYPE_LABEL`(`Record<CatalogItemType,String>`)로
 * 배너 라벨을 만들기 때문에 내부 도메인명(String)이 아니라 [CatalogItemType]으로 노출한다.
 * goods 도메인 실패는 PRODUCT/LIMITED_DROP 둘 다(또는 요청된 쪽만) 포함될 수 있어 도메인:타입이
 * 1:n이다.
 */
data class CatalogSearchResponse(
    val items: List<CatalogItem>,
    val page: Int,
    val size: Int,
    val failedDomains: List<CatalogItemType>,
)
