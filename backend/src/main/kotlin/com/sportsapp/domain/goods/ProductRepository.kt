package com.sportsapp.domain.goods

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findByOwnerId(ownerId: Long): List<Product>
}
