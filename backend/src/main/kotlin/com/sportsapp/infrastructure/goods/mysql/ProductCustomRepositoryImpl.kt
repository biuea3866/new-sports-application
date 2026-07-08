package com.sportsapp.infrastructure.goods.mysql

import com.querydsl.core.BooleanBuilder
import com.querydsl.core.types.OrderSpecifier
import com.querydsl.jpa.impl.JPAQueryFactory
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.dto.ProductWithStock
import com.sportsapp.domain.goods.entity.QProduct.product
import com.sportsapp.domain.goods.entity.QStock.stock
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import java.math.BigDecimal
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Repository

@Repository
class ProductCustomRepositoryImpl : ProductCustomRepository {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    private val queryFactory: JPAQueryFactory
        get() = JPAQueryFactory(entityManager)

    override fun search(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
        pageable: Pageable,
    ): Page<ProductWithStock> {
        val condition = buildCondition(category, keyword, priceMin, priceMax)
        val orderSpecifiers = buildOrderSpecifiers(pageable)
        val content = fetchContent(condition, orderSpecifiers, pageable)
        val total = fetchCount(condition)
        return PageImpl(content, pageable, total)
    }

    private fun buildCondition(
        category: ProductCategory?,
        keyword: String?,
        priceMin: BigDecimal?,
        priceMax: BigDecimal?,
    ): BooleanBuilder {
        val builder = BooleanBuilder()
        builder.and(product.deletedAt.isNull)
        category?.let { builder.and(product.category.eq(it)) }
        keyword?.takeIf { it.isNotBlank() }?.let { builder.and(product.name.containsIgnoreCase(it)) }
        priceMin?.let { builder.and(product.price.goe(it)) }
        priceMax?.let { builder.and(product.price.loe(it)) }
        return builder
    }

    private fun buildOrderSpecifiers(pageable: Pageable): List<OrderSpecifier<*>> =
        pageable.sort.map { order ->
            if (order.property == "price") product.price.asc()
            else product.createdAt.desc()
        }.toList()

    private fun fetchContent(
        condition: BooleanBuilder,
        orderSpecifiers: List<OrderSpecifier<*>>,
        pageable: Pageable,
    ): List<ProductWithStock> {
        val query = queryFactory.select(product, stock)
                                .from(product)
                                .leftJoin(stock).on(stock.productId.eq(product.id))
                                .where(condition)
                                .offset(pageable.offset)
                                .limit(pageable.pageSize.toLong())

        orderSpecifiers.forEach { query.orderBy(it) }

        return query.fetch().map { tuple ->
            val fetchedProduct = requireNotNull(tuple.get(product)) { "product tuple must not be null" }
            ProductWithStock(
                product = fetchedProduct,
                stockQuantity = tuple.get(stock)?.quantity ?: 0,
            )
        }
    }

    override fun findByOwnerId(ownerId: Long, pageable: Pageable): Page<ProductWithStock> {
        val condition = BooleanBuilder()
            .and(product.ownerId.eq(ownerId))
            .and(product.deletedAt.isNull)

        val content = queryFactory.select(product, stock)
                                  .from(product)
                                  .leftJoin(stock).on(stock.productId.eq(product.id))
                                  .where(condition)
                                  .offset(pageable.offset)
                                  .limit(pageable.pageSize.toLong())
                                  .orderBy(product.createdAt.desc())
                                  .fetch()
                                  .map { tuple ->
                                      val fetchedProduct = requireNotNull(tuple.get(product)) { "product tuple must not be null" }
                                      ProductWithStock(
                                          product = fetchedProduct,
                                          stockQuantity = tuple.get(stock)?.quantity ?: 0,
                                      )
                                  }

        val total = queryFactory.select(product.count())
                                .from(product)
                                .where(condition)
                                .fetchOne() ?: 0L

        return PageImpl(content, pageable, total)
    }

    private fun fetchCount(condition: BooleanBuilder): Long =
        queryFactory.select(product.count())
                    .from(product)
                    .where(condition)
                    .fetchOne() ?: 0L
}
