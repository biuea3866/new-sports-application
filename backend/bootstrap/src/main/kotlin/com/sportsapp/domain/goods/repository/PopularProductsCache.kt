package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.dto.PopularProductSnapshot
import com.sportsapp.domain.goods.vo.ProductCategory

interface PopularProductsCache {
    fun get(category: ProductCategory): List<PopularProductSnapshot>?
    fun put(category: ProductCategory, snapshots: List<PopularProductSnapshot>)
    fun invalidate(category: ProductCategory)
}
