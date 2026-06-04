package com.sportsapp.infrastructure.goods.redis

import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import java.math.BigDecimal

data class PopularProductCacheDto(
    val id: Long,
    val name: String,
    val category: ProductCategory,
    val price: BigDecimal,
    val description: String,
    val imageUrl: String,
    val status: ProductStatus,
) {
    fun toSnapshot(): PopularProductSnapshot = PopularProductSnapshot(
        id = id,
        name = name,
        category = category,
        price = price,
        description = description,
        imageUrl = imageUrl,
        status = status,
    )

    companion object {
        fun of(snapshot: PopularProductSnapshot): PopularProductCacheDto = PopularProductCacheDto(
            id = snapshot.id,
            name = snapshot.name,
            category = snapshot.category,
            price = snapshot.price,
            description = snapshot.description,
            imageUrl = snapshot.imageUrl,
            status = snapshot.status,
        )
    }
}
