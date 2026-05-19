package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import org.springframework.stereotype.Component

@Component
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {

    override fun save(stock: Stock): Stock {
        val existing = stockJpaRepository.findByProductId(stock.productId)
        val entity = if (existing != null) {
            existing.quantity = stock.quantity
            existing
        } else {
            StockEntity(
                productId = stock.productId,
                quantity = stock.quantity,
                version = 0L,
            )
        }
        return stockJpaRepository.save(entity).toDomain()
    }

    override fun findByProductId(productId: Long): Stock? =
        stockJpaRepository.findByProductId(productId)?.toDomain()

    override fun deleteAll() = stockJpaRepository.deleteAll()
}
