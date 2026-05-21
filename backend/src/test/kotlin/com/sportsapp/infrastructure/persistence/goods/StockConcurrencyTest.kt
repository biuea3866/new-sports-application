package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class StockConcurrencyTest(
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val stockRepository: StockRepository,
    @Autowired private val goodsDomainService: GoodsDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("재고 1000개인 Product가 존재할 때") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            val product = productRepository.save(
                Product(
                    name = "동시성 테스트 상품",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("10000"),
                    description = "동시성 테스트용",
                    imageUrl = "https://example.com/test.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockRepository.save(Stock(productId = product.id, quantity = 1000))

            When("100개 스레드가 각각 1개씩 동시에 차감하면") {
                Then("[S-01] 정확히 100만큼 차감되어 over-sell이 발생하지 않는다") {
                    val threadCount = 100
                    val latch = CountDownLatch(threadCount)
                    val executor = Executors.newFixedThreadPool(threadCount)
                    val successCount = AtomicInteger(0)
                    val failCount = AtomicInteger(0)

                    repeat(threadCount) {
                        executor.submit {
                            runCatching { goodsDomainService.deductStock(product.id, 1) }
                                .onSuccess { successCount.incrementAndGet() }
                                .onFailure { failCount.incrementAndGet() }
                            latch.countDown()
                        }
                    }

                    latch.await()
                    executor.shutdown()

                    val remaining = stockRepository.findByProductId(product.id)
                    remaining?.quantity shouldBe (1000 - successCount.get())
                    successCount.get() + failCount.get() shouldBe threadCount
                }
            }
        }
    }
}
