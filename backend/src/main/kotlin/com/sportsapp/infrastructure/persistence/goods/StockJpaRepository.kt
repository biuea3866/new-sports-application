package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?
}
