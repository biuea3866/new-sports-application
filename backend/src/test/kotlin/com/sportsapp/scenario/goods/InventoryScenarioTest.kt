package com.sportsapp.scenario.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.goods.GetInventoryCommand
import com.sportsapp.application.goods.GetInventoryUseCase
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.infrastructure.persistence.goods.ProductJpaRepository
import com.sportsapp.infrastructure.persistence.goods.StockJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class InventoryScenarioTest(
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val getInventoryUseCase: GetInventoryUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun createProductWithStock(name: String, ownerId: Long, quantity: Int) {
        val product = productJpaRepository.save(
            Product(
                name = name,
                category = ProductCategory.EQUIPMENT,
                price = BigDecimal("10000"),
                description = "테스트 상품",
                imageUrl = "https://example.com/img.jpg",
                status = ProductStatus.ACTIVE,
                ownerId = ownerId,
            )
        )
        stockJpaRepository.save(Stock(productId = product.id, quantity = quantity))
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE stocks")
            jdbcTemplate.execute("TRUNCATE TABLE products")
        }

        Given("[S-02] 재고 5개 이하 상품 3건 존재") {
            createProductWithStock("상품A", 1L, 3)
            createProductWithStock("상품B", 1L, 2)
            createProductWithStock("상품C", 1L, 1)
            createProductWithStock("상품D", 1L, 100)

            When("[S-02] lowStockOnly=true 호출 시") {
                val command = GetInventoryCommand(operatorUserId = 1L, lowStockOnly = true)
                val response = getInventoryUseCase.execute(command)

                Then("[S-02] 3건만 반환된다") {
                    response.items.size shouldBe 3
                    response.items.all { it.lowStock } shouldBe true
                }
            }
        }
    }
}
