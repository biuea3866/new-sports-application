package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<Stock, Long> {
    fun findByProductId(productId: Long): Stock?
}
