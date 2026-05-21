package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class StockRepositoryQueryTest(
    @Autowired private val stockRepository: StockRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE stocks")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    private fun saveProductWithStock(ownerId: Long, quantity: Int): Product {
        val product = productJpaRepository.save(
            Product(
                name = "테스트 상품",
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("10000"),
                description = "설명",
                imageUrl = "https://example.com/img.jpg",
                status = ProductStatus.ACTIVE,
                ownerId = ownerId,
            )
        )
        stockJpaRepository.save(Stock(productId = product.id, quantity = quantity))
        return product
    }

    init {
        afterEach { resetData() }

        Given("[R-01] ownerId=1인 소유자의 품절 상품이 2건, 재고 있는 상품이 1건 존재할 때") {
            resetData()
            saveProductWithStock(ownerId = 1L, quantity = 0)
            saveProductWithStock(ownerId = 1L, quantity = 0)
            saveProductWithStock(ownerId = 1L, quantity = 5)
            saveProductWithStock(ownerId = 2L, quantity = 0)

            When("countOutOfStockByOwnerId(1L)을 호출하면") {
                Then("[R-01] ownerId=1의 품절 상품 2건만 반환된다") {
                    val count = stockRepository.countOutOfStockByOwnerId(1L)
                    count shouldBe 2L
                }
            }
        }

        Given("[R-02] 품절 상품이 없는 소유자의 경우") {
            resetData()
            saveProductWithStock(ownerId = 3L, quantity = 10)

            When("countOutOfStockByOwnerId(3L)을 호출하면") {
                Then("[R-02] 0이 반환된다") {
                    val count = stockRepository.countOutOfStockByOwnerId(3L)
                    count shouldBe 0L
                }
            }
        }
    }
}
