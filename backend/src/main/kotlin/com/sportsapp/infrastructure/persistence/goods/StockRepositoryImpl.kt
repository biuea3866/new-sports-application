package com.sportsapp.infrastructure.persistence.goods

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.QProduct.product
import com.sportsapp.domain.goods.QStock.stock
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class StockRepositoryImpl(
    private val stockJpaRepository: StockJpaRepository,
) : StockRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

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
