package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: Long): Product?
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findByOwnerId(ownerId: Long): List<Product>
    fun countByOwnerIdAndStatus(ownerId: Long, status: ProductStatus): Long
}
