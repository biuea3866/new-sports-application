package com.sportsapp.domain.goods.dto
import com.sportsapp.domain.goods.entity.LimitedDropStatus
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import java.math.BigDecimal

/**
 * [limitedDropId]는 이 상품에 연결된 활성(SCHEDULED|OPEN|SOLD_OUT) 한정판 회차 id다(없으면 null).
 * [limitedDropStatus]는 그 회차의 실시간 파생 상태([com.sportsapp.domain.goods.entity.LimitedDrop
 * .effectiveStatus]) — `limitedDropId`가 채워질 때 항상 함께 채워진다(catalog 통합검색이 품절
 * 한정판을 ACTIVE로 오노출하지 않기 위해 필요, BE-07 코드 리뷰).
 * [GoodsDomainService][com.sportsapp.domain.goods.service.GoodsDomainService]가 조회 시점에
 * LimitedDropRepository·DropReservationStore로 결합해 채운다 — 상품 조회 응답의 한정판 진입점
 * 배너용.
 */
data class ProductWithStock(
    val product: Product,
    val stockQuantity: Int,
    val limitedDropId: Long? = null,
    val limitedDropStatus: LimitedDropStatus? = null,
) {
    val price: BigDecimal get() = product.price

    fun requireOwnedBy(ownerUserId: Long) {
        product.requireOwnedBy(ownerUserId)
    }

    fun validateQuantityWithin(requested: Int) {
        if (requested > stockQuantity) {
            throw LimitedDropQuantityExceedsStockException(product.id, requested, stockQuantity)
        }
    }
}
