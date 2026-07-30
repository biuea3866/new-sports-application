package com.sportsapp.infrastructure.goods.mysql

import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.repository.StockCustomRepository
import com.sportsapp.domain.goods.entity.QProduct.product
import com.sportsapp.domain.goods.entity.QStock.stock
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository

@Repository
class StockCustomRepositoryImpl : StockCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

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
