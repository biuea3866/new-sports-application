package com.sportsapp.domain.goods

interface PopularProductsCache {
    fun get(category: ProductCategory): List<Product>?
    fun put(category: ProductCategory, products: List<Product>)
    fun invalidate(category: ProductCategory)
}
