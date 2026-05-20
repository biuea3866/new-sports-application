package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<Product, Long> {
    fun findAllByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<Product>
    fun findAllByOwnerIdAndDeletedAtIsNull(ownerId: Long): List<Product>
}
