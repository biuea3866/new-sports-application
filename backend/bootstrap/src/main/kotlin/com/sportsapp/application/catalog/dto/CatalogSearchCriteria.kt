package com.sportsapp.application.catalog.dto

import com.sportsapp.domain.goods.vo.SellerType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

/**
 * catalog 통합검색 조건 (BE-07). [itemType] 지정 시 해당 도메인만 조회하고, [sellerType]은
 * PRODUCT/LIMITED_DROP(goods) 조회에만 적용된다.
 */
data class CatalogSearchCriteria(
    val keyword: String?,
    val itemType: CatalogItemType?,
    val sellerType: SellerType?,
    val page: Int,
    val size: Int,
) {
    val cappedSize: Int
        get() = minOf(size, MAX_PAGE_SIZE).coerceAtLeast(1)

    /**
     * 여러 도메인을 병합·정렬한 뒤 in-memory로 페이지네이션하므로, 각 도메인에는 최종 페이지를
     * 덮는 넉넉한 창(offset 0, `(page+1)*cappedSize`)을 createdAt desc로 요청한다.
     */
    fun toDomainPageable(): Pageable =
        PageRequest.of(0, (page + 1) * cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"))

    companion object {
        const val MAX_PAGE_SIZE = 100
    }
}
