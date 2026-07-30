package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.repository.StockCustomRepository
import com.sportsapp.domain.goods.repository.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
    private val stockCustomRepository: StockCustomRepository,
) : StockRepository {

    override fun save(stock: Stock): Stock = stockJpaRepository.save(stock)

    override fun findByProductId(productId: Long): Stock? =
        stockJpaRepository.findByProductId(productId)

    override fun countOutOfStockByOwnerId(ownerId: Long): Long =
        stockCustomRepository.countOutOfStockByOwnerId(ownerId)
}
