package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class GoodsOrderRepositoryTest(
    @Autowired private val goodsOrderRepository: GoodsOrderRepository,
    @Autowired private val goodsOrderItemRepository: GoodsOrderItemRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
        }

        Given("GoodsOrder 저장 후 조회") {
            When("GoodsOrder를 save하고 findById로 조회하면") {
                Then("[R-01] 저장된 필드가 정확히 복원된다") {
                    val order = goodsOrderRepository.save(
                        GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("30000"))
                    )
                    order.id shouldNotBe 0L
                    val found = goodsOrderRepository.findById(order.id)
                    found shouldNotBe null
                    requireNotNull(found)
                    found.userId shouldBe 1L
                    found.status shouldBe GoodsOrderStatus.PENDING
                    found.totalAmount.compareTo(BigDecimal("30000")) shouldBe 0
                    found.paymentId shouldBe null
                }
            }
        }

        Given("GoodsOrderItem 저장 후 조회") {
            When("GoodsOrderItem을 saveAll하고 findByOrderId로 조회하면") {
                Then("[R-01] 저장된 아이템 목록이 정확히 복원된다") {
                    val order = goodsOrderRepository.save(
                        GoodsOrder.create(userId = 2L, totalAmount = BigDecimal("50000"))
                    )
                    val items = listOf(
                        GoodsOrderItem(
                            orderId = order.id,
                            productId = 10L,
                            quantity = 2,
                            unitPrice = BigDecimal("15000"),
                        ),
                        GoodsOrderItem(
                            orderId = order.id,
                            productId = 20L,
                            quantity = 1,
                            unitPrice = BigDecimal("20000"),
                        ),
                    )
                    goodsOrderItemRepository.saveAll(items)

                    val found = goodsOrderItemRepository.findByOrderId(order.id)
                    found.size shouldBe 2
                    found.any { it.productId == 10L && it.quantity == 2 } shouldBe true
                    found.any { it.productId == 20L && it.quantity == 1 } shouldBe true
                }
            }
        }

        Given("userId로 주문 목록 조회") {
            When("userId로 findByUserId 페이징 조회하면") {
                Then("[R-02] 해당 userId의 활성 주문만 반환된다") {
                    goodsOrderRepository.save(GoodsOrder.create(userId = 10L, totalAmount = BigDecimal("10000")))
                    goodsOrderRepository.save(GoodsOrder.create(userId = 10L, totalAmount = BigDecimal("20000")))
                    goodsOrderRepository.save(GoodsOrder.create(userId = 99L, totalAmount = BigDecimal("50000")))

                    val page = goodsOrderRepository.findByUserId(10L, PageRequest.of(0, 10))
                    page.totalElements shouldBe 2
                }
            }
        }
    }
}
