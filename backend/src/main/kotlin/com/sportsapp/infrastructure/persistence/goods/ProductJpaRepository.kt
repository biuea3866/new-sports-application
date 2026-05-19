package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import org.springframework.data.jpa.repository.JpaRepository

interface ProductJpaRepository : JpaRepository<ProductEntity, Long> {
    fun findAllByCategoryAndStatus(category: ProductCategory, status: ProductStatus): List<ProductEntity>
}
