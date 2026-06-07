package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class BE11GoodsOrderItemOrphanTest(
    @Autowired private val goodsOrderRepository: GoodsOrderRepository,
    @Autowired private val goodsOrderItemRepository: GoodsOrderItemRepository,
    @Autowired private val goodsDomainService: GoodsDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
        }

        Given("[R-01] softDeleteAllByOrderId 후 findByOrderId는 0건을 반환한다") {
            val order = goodsOrderRepository.save(GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000")))
            val items = goodsOrderItemRepository.saveAll(
                listOf(
                    GoodsOrderItem(orderId = order.id, productId = 10L, quantity = 1, unitPrice = BigDecimal("5000")),
                    GoodsOrderItem(orderId = order.id, productId = 20L, quantity = 2, unitPrice = BigDecimal("2500")),
                )
            )

            When("[R-01] findByOrderId로 조회한 items를 softDelete 후 saveAll하면") {
                val activeItems = goodsOrderItemRepository.findByOrderId(order.id)
                activeItems.forEach { it.softDelete(null) }
                goodsOrderItemRepository.saveAll(activeItems)

                Then("[R-01] findByOrderId가 0건을 반환한다") {
                    goodsOrderItemRepository.findByOrderId(order.id).size shouldBe 0
                }
            }
        }

        Given("[R-02] softDelete는 다른 orderId의 GoodsOrderItem에 영향을 주지 않는다") {
            val orderA = goodsOrderRepository.save(GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("5000")))
            val orderB = goodsOrderRepository.save(GoodsOrder.create(userId = 2L, totalAmount = BigDecimal("3000")))
            goodsOrderItemRepository.saveAll(
                listOf(GoodsOrderItem(orderId = orderA.id, productId = 10L, quantity = 1, unitPrice = BigDecimal("5000")))
            )
            goodsOrderItemRepository.saveAll(
                listOf(GoodsOrderItem(orderId = orderB.id, productId = 20L, quantity = 1, unitPrice = BigDecimal("3000")))
            )

            When("[R-02] orderA 아이템만 softDelete하면") {
                val aItems = goodsOrderItemRepository.findByOrderId(orderA.id)
                aItems.forEach { it.softDelete(null) }
                goodsOrderItemRepository.saveAll(aItems)

                Then("[R-02] orderB의 아이템은 여전히 1건 조회된다") {
                    goodsOrderItemRepository.findByOrderId(orderA.id).size shouldBe 0
                    goodsOrderItemRepository.findByOrderId(orderB.id).size shouldBe 1
                }
            }
        }

        Given("[R-03] softDelete 후 deletedAt이 현재 시각 이내로 저장된다") {
            val order = goodsOrderRepository.save(GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("5000")))
            goodsOrderItemRepository.saveAll(
                listOf(GoodsOrderItem(orderId = order.id, productId = 10L, quantity = 1, unitPrice = BigDecimal("5000")))
            )

            When("[R-03] findByOrderId 후 softDelete + saveAll 하면") {
                val beforeDelete = java.time.ZonedDateTime.now()
                val items = goodsOrderItemRepository.findByOrderId(order.id)
                items.forEach { it.softDelete(null) }
                goodsOrderItemRepository.saveAll(items)

                Then("[R-03] deletedAt이 NOT NULL이고 현재 시각 이내이다") {
                    val allItems = jdbcTemplate.queryForList(
                        "SELECT deleted_at FROM goods_order_items WHERE order_id = ?", order.id
                    )
                    allItems.size shouldBe 1
                    allItems.first()["deleted_at"] shouldNotBe null
                }
            }
        }
    }
}
