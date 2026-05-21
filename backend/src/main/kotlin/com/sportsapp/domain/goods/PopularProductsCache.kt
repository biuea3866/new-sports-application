package com.sportsapp.domain.goods

interface PopularProductsCache {
    fun get(category: ProductCategory): List<PopularProductSnapshot>?
    fun put(category: ProductCategory, snapshots: List<PopularProductSnapshot>)
    fun invalidate(category: ProductCategory)
}
