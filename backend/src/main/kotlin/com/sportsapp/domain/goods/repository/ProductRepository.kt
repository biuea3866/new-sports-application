package com.sportsapp.domain.goods.repository
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory

interface ProductRepository {
    fun save(product: Product): Product
    fun saveAll(products: List<Product>): List<Product>
    fun findById(id: Long): Product?
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun findByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findByOwnerId(ownerId: Long): List<Product>
    fun countByOwnerIdAndStatus(ownerId: Long, status: ProductStatus): Long

    /** BE-11 배치 백필 검증 스텝 — 잔여 NULL 건수 확인용. */
    fun countBySellerTypeIsNull(): Long
}
