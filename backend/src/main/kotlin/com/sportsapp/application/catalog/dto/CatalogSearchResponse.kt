package com.sportsapp.application.catalog.dto

/**
 * catalog 통합검색 응답 (BE-07). [failedDomains]는 300ms 타임아웃/조회 실패로 결과에서
 * 제외된 도메인명을 담는다(FR-11) — 비어 있으면 5개 도메인 모두 정상 조회됐다는 뜻이다.
 */
data class CatalogSearchResponse(
    val items: List<CatalogItem>,
    val page: Int,
    val size: Int,
    val failedDomains: List<String>,
)
