package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {

    override fun save(stock: Stock): Stock = stockJpaRepository.save(stock)

    override fun findByProductId(productId: Long): Stock? =
        stockJpaRepository.findByProductId(productId)

    override fun countOutOfStockByOwnerId(ownerId: Long): Long =
        stockJpaRepository.countOutOfStockByOwnerId(ownerId)
}
