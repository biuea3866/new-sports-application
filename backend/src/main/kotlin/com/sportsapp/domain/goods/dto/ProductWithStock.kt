package com.sportsapp.domain.goods.dto
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.exception.LimitedDropQuantityExceedsStockException

data class ProductWithStock(
    val product: Product,
    val stockQuantity: Int,
) {
    fun requireOwnedBy(ownerUserId: Long) {
        product.requireOwnedBy(ownerUserId)
    }

    fun validateQuantityWithin(requested: Int) {
        if (requested > stockQuantity) {
            throw LimitedDropQuantityExceedsStockException(product.id, requested, stockQuantity)
        }
    }
}
