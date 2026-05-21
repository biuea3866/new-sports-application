package com.sportsapp.infrastructure.persistence.goods

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.QProduct.product
import com.sportsapp.domain.goods.QStock.stock
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
    private val queryFactory: JPAQueryFactory,
) : StockRepository {

    override fun save(stock: Stock): Stock = stockJpaRepository.save(stock)

    override fun findByProductId(productId: Long): Stock? =
        stockJpaRepository.findByProductId(productId)

    override fun countOutOfStockByOwnerId(ownerId: Long): Long =
        queryFactory.select(stock.count())
                    .from(stock)
                    .join(product).on(product.id.eq(stock.productId))
                    .where(
                        product.ownerId.eq(ownerId),
                        product.deletedAt.isNull,
                        stock.deletedAt.isNull,
                        stock.quantity.eq(0),
                    )
                    .fetchOne() ?: 0L
}
