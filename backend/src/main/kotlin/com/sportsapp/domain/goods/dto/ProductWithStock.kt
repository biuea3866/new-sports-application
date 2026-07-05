package com.sportsapp.domain.goods.dto
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException
import java.math.BigDecimal

/**
 * [limitedDropId]는 이 상품에 연결된 활성(SCHEDULED|OPEN|SOLD_OUT) 한정판 회차 id다(없으면 null).
 * [GoodsDomainService][com.sportsapp.domain.goods.service.GoodsDomainService]가 조회 시점에
 * LimitedDropRepository로 결합해 채운다 — 상품 조회 응답의 한정판 진입점 배너용.
 */
data class ProductWithStock(
    val product: Product,
    val stockQuantity: Int,
    val limitedDropId: Long? = null,
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
