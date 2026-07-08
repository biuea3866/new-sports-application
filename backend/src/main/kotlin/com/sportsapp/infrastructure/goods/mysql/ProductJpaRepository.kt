package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findByIdAndDeletedAtIsNull(id: Long): Product?
    fun findAllByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findAllByOwnerIdAndDeletedAtIsNull(ownerId: Long): List<Product>
    fun countByOwnerIdAndStatusAndDeletedAtIsNull(ownerId: Long, status: ProductStatus): Long
}
