package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class B2bProductRepositoryTest(
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val stockRepository: StockRepository,
    @Autowired private val goodsDomainService: GoodsDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE stocks")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    init {
        afterEach { resetData() }

        Given("[R-02] 재고 0인 Product에 restoreStock(10)을 호출하면") {
            resetData()
            val product = productJpaRepository.save(
                Product(
                    name = "테니스 라켓",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("50000"),
                    description = "고급 테니스 라켓",
                    imageUrl = "https://example.com/racket.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = 0))

            When("restoreStock을 호출하면") {
                goodsDomainService.restoreStock(product.id, 10)

                Then("[R-02] Stock.quantity가 10으로 영속화된다") {
                    val stock = stockJpaRepository.findByProductId(product.id)
                    requireNotNull(stock)
                    stock.quantity shouldBe 10
                }
            }
        }

        Given("[R-02] 재고 5인 Product에 restoreStock(7)을 호출하면") {
            resetData()
            val product = productJpaRepository.save(
                Product(
                    name = "배드민턴 라켓",
                    category = ProductCategory.EQUIPMENT,
                    price = BigDecimal("30000"),
                    description = "배드민턴 라켓",
                    imageUrl = "https://example.com/racket.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )
            stockJpaRepository.save(Stock(productId = product.id, quantity = 5))

            When("restoreStock을 호출하면") {
                goodsDomainService.restoreStock(product.id, 7)

                Then("[R-02] Stock.quantity가 12로 누적 영속화된다") {
                    val stock = stockJpaRepository.findByProductId(product.id)
                    requireNotNull(stock)
                    stock.quantity shouldBe 12
                }
            }
        }
    }
}
