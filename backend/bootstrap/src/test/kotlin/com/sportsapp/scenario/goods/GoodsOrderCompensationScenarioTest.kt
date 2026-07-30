package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.goods.dto.CreateGoodsOrderCommand
import com.sportsapp.application.goods.usecase.CreateGoodsOrderUseCase
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.infrastructure.goods.mysql.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID

class GoodsOrderCompensationScenarioTest(
    @Autowired private val createGoodsOrderUseCase: CreateGoodsOrderUseCase,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM payments")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
        }

        Given("재고 있는 상품으로 주문 생성 시") {
            var productId = 0L

            beforeEach {
                val product = productJpaRepository.save(
                    Product(
                        name = "보상테스트상품",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("15000"),
                        description = "보상 트랜잭션 테스트",
                        imageUrl = "https://example.com/comp.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 5))
                productId = product.id
            }

            When("createGoodsOrderUseCase를 성공적으로 호출하면") {
                Then("주문이 PENDING 상태로 저장되고 동기 확정은 일어나지 않는다") {
                    val command = CreateGoodsOrderCommand(
                        userId = 1L,
                        idempotencyKey = UUID.randomUUID().toString(),
                        method = PaymentMethod.CREDIT_CARD,
                        fromCart = false,
                        items = listOf(OrderItemInput(productId = productId, quantity = 2)),
                    )

                    createGoodsOrderUseCase.execute(command)

                    val orders = goodsOrderJpaRepository.findAll()
                    orders.size shouldBe 1
                    orders.first().status shouldBe GoodsOrderStatus.PENDING

                    val stock = requireNotNull(stockJpaRepository.findByProductId(productId))
                    stock.quantity shouldBe 3
                }
            }
        }
    }
}
