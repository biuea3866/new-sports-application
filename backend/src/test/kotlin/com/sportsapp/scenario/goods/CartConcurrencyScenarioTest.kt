package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.goods.dto.AddCartItemCommand
import com.sportsapp.application.goods.usecase.AddCartItemUseCase
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.entity.Stock
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import com.sportsapp.infrastructure.goods.mysql.StockJpaRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class CartConcurrencyScenarioTest(
    @Autowired private val addCartItemUseCase: AddCartItemUseCase,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("재고가 충분한 ACTIVE 상품과 cart 가 없는 신규 user 가 있을 때") {
            val userId = 200L
            var productId = 0L

            beforeEach {
                jdbcTemplate.execute("DELETE FROM cart_items")
                jdbcTemplate.execute("DELETE FROM carts")
                jdbcTemplate.execute("DELETE FROM stocks")
                jdbcTemplate.execute("DELETE FROM products")

                val product = productJpaRepository.save(
                    Product(
                        name = "농구공",
                        category = ProductCategory.EQUIPMENT,
                        price = BigDecimal("30000"),
                        description = "실내용",
                        imageUrl = "https://example.com/ball.jpg",
                        status = ProductStatus.ACTIVE,
                        ownerId = 1L,
                    )
                )
                stockJpaRepository.save(Stock(productId = product.id, quantity = 100))
                productId = product.id
            }

            When("같은 상품을 10개 스레드가 동시에 장바구니에 추가하면") {
                Then("5xx 예외 0건 + 활성 cart 1건 + 활성 cart_item 1건 + 수량은 정확히 10으로 수렴한다") {
                    val threadCount = 10
                    val executor = Executors.newFixedThreadPool(threadCount)
                    val ready = CountDownLatch(threadCount)
                    val start = CountDownLatch(1)
                    val done = CountDownLatch(threadCount)
                    val errors = ConcurrentLinkedQueue<Throwable>()

                    repeat(threadCount) {
                        executor.submit {
                            ready.countDown()
                            start.await()
                            try {
                                addCartItemUseCase.execute(
                                    AddCartItemCommand(userId = userId, productId = productId, quantity = 1)
                                )
                            } catch (e: Throwable) {
                                errors.add(e)
                            } finally {
                                done.countDown()
                            }
                        }
                    }

                    ready.await(10, TimeUnit.SECONDS)
                    start.countDown()
                    done.await(60, TimeUnit.SECONDS)
                    executor.shutdownNow()

                    // 1) 동시 경합에서 5xx(미변환 예외) 0건
                    errors.shouldBeEmpty()

                    // 2) 활성 cart 단일 수렴
                    val activeCarts = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM carts WHERE user_id = ? AND deleted_at IS NULL",
                        Long::class.java,
                        userId,
                    )
                    activeCarts shouldBe 1L

                    // 3) 활성 cart_item 단일 수렴 (중복 활성 row 없음)
                    val activeItems = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM cart_items WHERE product_id = ? AND deleted_at IS NULL",
                        Long::class.java,
                        productId,
                    )
                    activeItems shouldBe 1L

                    // 4) 수량 정확 합산 (lost update 없음 — @Version + 재시도)
                    val quantity = jdbcTemplate.queryForObject(
                        "SELECT quantity FROM cart_items WHERE product_id = ? AND deleted_at IS NULL",
                        Int::class.java,
                        productId,
                    )
                    quantity shouldBe 10
                }
            }
        }
    }
}
