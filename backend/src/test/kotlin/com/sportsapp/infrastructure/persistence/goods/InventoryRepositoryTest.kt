package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class InventoryRepositoryTest(
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val customGoodsRepositoryImpl: CustomGoodsRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE stocks")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    private fun createProductWithStock(name: String, ownerId: Long, quantity: Int): Product {
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
        return product
    }

    init {
        beforeEach { resetData() }

        Given("[R-02] 재고 5개 이하 상품 3건과 정상 재고 상품 1건이 존재 (lowStockOnly=true)") {
            createProductWithStock("상품A", 1L, 3)
            createProductWithStock("상품B", 1L, 5)
            createProductWithStock("상품C", 1L, 2)
            createProductWithStock("상품D", 1L, 100)

            When("[R-02] lowStockOnly=true로 조회 시") {
                val result = customGoodsRepositoryImpl.findInventory(ownerUserId = 1L, lowStockOnly = true)

                Then("[R-02] getInventory 쿼리가 quantity <= threshold 조건으로 low-stock 상품만 반환한다") {
                    result shouldHaveSize 3
                    result.all { it.lowStock } shouldBe true
                    result.all { it.stockQuantity <= 5 } shouldBe true
                }
            }
        }

        Given("[R-02b] 재고 5개 이하 상품 3건과 정상 재고 상품 1건이 존재 (lowStockOnly=false)") {
            createProductWithStock("상품A", 1L, 3)
            createProductWithStock("상품B", 1L, 5)
            createProductWithStock("상품C", 1L, 2)
            createProductWithStock("상품D", 1L, 100)

            When("[R-02b] lowStockOnly=false로 조회 시") {
                val result = customGoodsRepositoryImpl.findInventory(ownerUserId = 1L, lowStockOnly = false)

                Then("[R-02b] 전체 4건이 반환된다") {
                    result shouldHaveSize 4
                }
            }
        }

        Given("[R-02c] 다른 운영자(ownerId=2) 상품은 필터링되어야 함") {
            createProductWithStock("상품A", 1L, 3)
            createProductWithStock("상품X", 2L, 3)

            When("[R-02c] ownerId=1로 조회 시") {
                val result = customGoodsRepositoryImpl.findInventory(ownerUserId = 1L, lowStockOnly = false)

                Then("[R-02c] ownerId=1 상품 1건만 반환된다") {
                    result shouldHaveSize 1
                    result[0].productName shouldBe "상품A"
                }
            }
        }
    }
}
