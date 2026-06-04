package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.goods.CreateGoodsOrderCommand
import com.sportsapp.application.goods.CreateGoodsOrderUseCase
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.infrastructure.persistence.goods.GoodsOrderItemJpaRepository
import com.sportsapp.infrastructure.persistence.goods.GoodsOrderJpaRepository
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class GoodsOrderTransactionTest(
    @Autowired private val createGoodsOrderUseCase: CreateGoodsOrderUseCase,
    @Autowired private val goodsDomainService: GoodsDomainService,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val goodsOrderItemJpaRepository: GoodsOrderItemJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        beforeEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM payments")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
        }

        afterEach {
            jdbcTemplate.execute("DELETE FROM goods_order_items")
            jdbcTemplate.execute("DELETE FROM goods_orders")
            jdbcTemplate.execute("DELETE FROM payments")
            jdbcTemplate.execute("DELETE FROM stocks")
            jdbcTemplate.execute("DELETE FROM products")
        }

        Given("[R-01] 3개 아이템 중 마지막 1건의 Stock이 부족한 상황") {
            var productId1 = 0L
            var productId2 = 0L
            var productId3 = 0L

            beforeEach {
                val p1 = productJpaRepository.save(
                    Product(
                        name = "상품A",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("10000"),
                        description = "설명A",
                        imageUrl = "https://example.com/a.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                val p2 = productJpaRepository.save(
                    Product(
                        name = "상품B",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("20000"),
                        description = "설명B",
                        imageUrl = "https://example.com/b.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                val p3 = productJpaRepository.save(
                    Product(
                        name = "상품C",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "설명C",
                        imageUrl = "https://example.com/c.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = p1.id, quantity = 5))
                stockJpaRepository.save(Stock(productId = p2.id, quantity = 5))
                stockJpaRepository.save(Stock(productId = p3.id, quantity = 1))
                productId1 = p1.id
                productId2 = p2.id
                productId3 = p3.id
            }

            When("createGoodsOrderUseCase를 호출하면 (3번째 아이템 수량 = 2로 재고 부족 유발)") {
                Then("[R-01] OutOfStockException 발생 후 Stock·GoodsOrder 모두 변화 없이 롤백된다") {
                    val command = CreateGoodsOrderCommand(
                        userId = 1L,
                        idempotencyKey = UUID.randomUUID().toString(),
                        method = PaymentMethod.CREDIT_CARD,
                        fromCart = false,
                        items = listOf(
                            OrderItemInput(productId = productId1, quantity = 2),
                            OrderItemInput(productId = productId2, quantity = 2),
                            OrderItemInput(productId = productId3, quantity = 2),
                        ),
                    )

                    runCatching { createGoodsOrderUseCase.execute(command) }
                        .isFailure shouldBe true

                    val stock1 = requireNotNull(stockJpaRepository.findByProductId(productId1))
                    val stock2 = requireNotNull(stockJpaRepository.findByProductId(productId2))
                    val stock3 = requireNotNull(stockJpaRepository.findByProductId(productId3))

                    stock1.quantity shouldBe 5
                    stock2.quantity shouldBe 5
                    stock3.quantity shouldBe 1

                    goodsOrderJpaRepository.count() shouldBe 0
                    goodsOrderItemJpaRepository.count() shouldBe 0
                }
            }
        }

        Given("[R-02] 3개 아이템 모두 충분한 Stock이 있는 상황") {
            var productId1 = 0L
            var productId2 = 0L
            var productId3 = 0L

            beforeEach {
                val p1 = productJpaRepository.save(
                    Product(
                        name = "원자성상품A",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("10000"),
                        description = "설명A",
                        imageUrl = "https://example.com/a.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                val p2 = productJpaRepository.save(
                    Product(
                        name = "원자성상품B",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("20000"),
                        description = "설명B",
                        imageUrl = "https://example.com/b.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                val p3 = productJpaRepository.save(
                    Product(
                        name = "원자성상품C",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "설명C",
                        imageUrl = "https://example.com/c.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = p1.id, quantity = 10))
                stockJpaRepository.save(Stock(productId = p2.id, quantity = 10))
                stockJpaRepository.save(Stock(productId = p3.id, quantity = 10))
                productId1 = p1.id
                productId2 = p2.id
                productId3 = p3.id
            }

            When("createGoodsOrderUseCase를 호출하면") {
                Then("[R-02] GoodsOrder 1건 + GoodsOrderItems 3건 + Stock 차감이 단일 트랜잭션 내 원자적으로 커밋된다") {
                    val command = CreateGoodsOrderCommand(
                        userId = 2L,
                        idempotencyKey = UUID.randomUUID().toString(),
                        method = PaymentMethod.CREDIT_CARD,
                        fromCart = false,
                        items = listOf(
                            OrderItemInput(productId = productId1, quantity = 2),
                            OrderItemInput(productId = productId2, quantity = 3),
                            OrderItemInput(productId = productId3, quantity = 1),
                        ),
                    )

                    createGoodsOrderUseCase.execute(command)

                    goodsOrderJpaRepository.count() shouldBe 1
                    goodsOrderItemJpaRepository.count() shouldBe 3

                    val stock1 = requireNotNull(stockJpaRepository.findByProductId(productId1))
                    val stock2 = requireNotNull(stockJpaRepository.findByProductId(productId2))
                    val stock3 = requireNotNull(stockJpaRepository.findByProductId(productId3))

                    stock1.quantity shouldBe 8
                    stock2.quantity shouldBe 7
                    stock3.quantity shouldBe 9
                }
            }
        }

        Given("[R-03] quantity=1 인 Stock이 있고 두 스레드가 동시에 차감을 시도하는 상황") {
            var productId = 0L

            beforeEach {
                val product = productJpaRepository.save(
                    Product(
                        name = "행잠금상품",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("10000"),
                        description = "행 잠금 테스트용",
                        imageUrl = "https://example.com/lock.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 1))
                productId = product.id
            }

            When("두 스레드가 동시에 deductStock(quantity=1)을 호출하면") {
                Then("[R-03] 한 스레드만 성공하고 최종 quantity=0 (over-sell 방지)") {
                    val startLatch = CountDownLatch(1)
                    val doneLatch = CountDownLatch(2)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)
                    val executor = Executors.newFixedThreadPool(2)

                    repeat(2) {
                        executor.submit {
                            startLatch.await()
                            runCatching {
                                goodsDomainService.deductStock(productId, 1)
                            }.onSuccess { successCount.incrementAndGet() }
                                .onFailure { failCount.incrementAndGet() }
                            doneLatch.countDown()
                        }
                    }

                    startLatch.countDown()
                    doneLatch.await()
                    executor.shutdown()

                    successCount.get() shouldBe 1
                    failCount.get() shouldBe 1

                    val remainingStock = requireNotNull(stockJpaRepository.findByProductId(productId))
                    remainingStock.quantity shouldBe 0
                }
            }
        }
    }
}
