package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.CustomStockRepository
import com.sportsapp.domain.goods.Stock
import org.springframework.data.jpa.repository.JpaRepository

interface StockJpaRepository : JpaRepository<Stock, Long>, CustomStockRepository {
    fun findByProductId(productId: Long): Stock?
}
