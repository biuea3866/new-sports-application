package com.sportsapp.application.goods.dto

import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.SellerType
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * edge catalog 통합검색(BE-07)이 `CatalogSearchGateway.searchGoods` 원격 구현(2단계)으로 소비할
 * 계약 응답 (S2-03).
 *
 * ## 한정판 판정은 공급자가 하지 않는다
 *
 * `CatalogItem` 의 `itemType`(PRODUCT/LIMITED_DROP)·`sourceId`(product.id vs limitedDropId)·
 * `detailPath`(`/products/{id}` vs `/limited-drops/{id}`)·`status`(product.status vs limitedDropStatus)는
 * **전부 한정판 여부에서 파생**되고, 그 파생은 지금 edge 파사드가 한다
 * (`LocalCatalogSearchAdapter.ProductWithStock.toCatalogItem`). 공급자는 판정에 필요한 **원자값**
 * ([productId]·[limitedDropId]·[limitedDropStatus]·[productStatus])을 그대로 실어 보내고
 * **매핑 위치를 옮기지 않는다** — 옮기면 섀도 응답 동일성 비교(S2-06·S2-15)가 성립하지 않는다.
 *
 * `locationName`·`scheduledAt` 은 goods 에 장소·일정 개념이 없어 항상 null 이라 이 응답에 두지
 * 않는다 — 상수 판정은 edge 파사드가 한다.
 *
 * [sellerType] 은 goods 소유 enum 이다. edge 는 자기 `SellerType`(catalog.vo)으로 변환해 쓰고,
 * 와이어 값(B2C/B2B)은 동일하다.
 */
data class InternalGoodsCatalogItemResponse(
    val productId: Long,
    val limitedDropId: Long?,
    val limitedDropStatus: LimitedDropStatus?,
    val title: String,
    val price: BigDecimal,
    val sellerType: SellerType?,
    val productStatus: ProductStatus,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(productWithStock: ProductWithStock): InternalGoodsCatalogItemResponse =
            InternalGoodsCatalogItemResponse(
                productId = productWithStock.product.id,
                limitedDropId = productWithStock.limitedDropId,
                limitedDropStatus = productWithStock.limitedDropStatus,
                title = productWithStock.product.name,
                price = productWithStock.product.price,
                sellerType = productWithStock.product.sellerType,
                productStatus = productWithStock.product.status,
                createdAt = productWithStock.product.createdAt,
            )
    }
}
