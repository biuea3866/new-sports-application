package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductRepository
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import com.sportsapp.domain.goods.StockRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class ProductRepositoryTest(
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val stockRepository: StockRepository,
) : BaseIntegrationTest() {

    init {
        Given("Product 저장 후 ZonedDateTime 라운드트립 검증") {
            afterEach {
                stockRepository.deleteAll()
                productRepository.deleteAll()
            }

            When("findById로 조회하면") {
                Then("[R-01 roundtrip] ZonedDateTime이 UTC로 저장되고 원본 instant와 동일하다") {
                    val originalTime = ZonedDateTime.of(2026, 1, 15, 12, 0, 0, 0, ZoneOffset.UTC)
                    val product = productRepository.save(
                        createProduct(createdAt = originalTime, updatedAt = originalTime)
                    )

                    val found = productRepository.findById(product.id)
                    found shouldNotBe null
                    found?.createdAt?.toInstant() shouldBe originalTime.toInstant()
                    found?.name shouldBe "테니스 라켓"
                    found?.price shouldBe BigDecimal("50000.00")
                    found?.category shouldBe ProductCategory.EQUIPMENT
                    found?.status shouldBe ProductStatus.ACTIVE
                }
            }
        }

        Given("복합 인덱스 검증 시나리오") {
            afterEach {
                stockRepository.deleteAll()
                productRepository.deleteAll()
            }

            When("category=EQUIPMENT, status=ACTIVE 조건으로 검색하면") {
                Then("[R-01 index] 필터링된 결과만 반환된다") {
                    val now = ZonedDateTime.now(ZoneOffset.UTC)
                    val product1 = productRepository.save(
                        createProduct(
                            name = "라켓1",
                            category = ProductCategory.EQUIPMENT,
                            status = ProductStatus.ACTIVE,
                            price = BigDecimal("50000"),
                            createdAt = now,
                            updatedAt = now,
                        )
                    )
                    productRepository.save(
                        createProduct(
                            name = "의류1",
                            category = ProductCategory.APPAREL,
                            status = ProductStatus.ACTIVE,
                            price = BigDecimal("30000"),
                            createdAt = now,
                            updatedAt = now,
                        )
                    )

                    val results = productRepository.findByCategoryAndStatus(
                        ProductCategory.EQUIPMENT,
                        ProductStatus.ACTIVE,
                    )
                    results.size shouldBe 1
                    results[0].id shouldBe product1.id
                }
            }
        }

        Given("Stock 저장 후 조회 시나리오") {
            afterEach {
                stockRepository.deleteAll()
                productRepository.deleteAll()
            }

            When("productId로 Stock을 조회하면") {
                Then("[R-02] 저장된 Stock을 정확히 반환한다") {
                    val now = ZonedDateTime.now(ZoneOffset.UTC)
                    val product = productRepository.save(
                        createProduct(createdAt = now, updatedAt = now)
                    )
                    stockRepository.save(Stock(productId = product.id, quantity = 100))

                    val found = stockRepository.findByProductId(product.id)
                    found shouldNotBe null
                    found?.quantity shouldBe 100
                    found?.productId shouldBe product.id
                }
            }
        }
    }

    private fun createProduct(
        name: String = "테니스 라켓",
        category: ProductCategory = ProductCategory.EQUIPMENT,
        status: ProductStatus = ProductStatus.ACTIVE,
        price: BigDecimal = BigDecimal("50000"),
        createdAt: ZonedDateTime,
        updatedAt: ZonedDateTime,
    ) = Product(
        id = 0L,
        name = name,
        category = category,
        price = price,
        description = "설명",
        imageUrl = "https://example.com/image.jpg",
        status = status,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
