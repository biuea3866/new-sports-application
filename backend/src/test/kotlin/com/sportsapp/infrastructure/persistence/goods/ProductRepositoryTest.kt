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
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class ProductRepositoryTest(
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val stockRepository: StockRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("Product 저장 후 조회 검증") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            When("findById로 조회하면") {
                Then("[R-01 roundtrip] 저장된 필드가 정확히 복원된다") {
                    val product = productRepository.save(createProduct())

                    val found = productRepository.findById(product.id)
                    found shouldNotBe null
                    found?.createdAt shouldNotBe null
                    found?.name shouldBe "테니스 라켓"
                    found?.price shouldBe BigDecimal("50000.00")
                    found?.category shouldBe ProductCategory.EQUIPMENT
                    found?.status shouldBe ProductStatus.ACTIVE
                }
            }

            When("save 후 findById로 조회하면") {
                Then("[R-01] owner_id 컬럼이 영속화되어 복원된다") {
                    val product = productRepository.save(createProduct(ownerId = 99L))

                    val found = productRepository.findById(product.id)
                    found shouldNotBe null
                    found?.ownerId shouldBe 99L
                }
            }
        }

        Given("복합 인덱스 검증 시나리오") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            When("category=EQUIPMENT, status=ACTIVE 조건으로 검색하면") {
                Then("[R-01 index] 필터링된 결과만 반환된다") {
                    val product1 = productRepository.save(
                        createProduct(name = "라켓1", category = ProductCategory.EQUIPMENT, status = ProductStatus.ACTIVE)
                    )
                    productRepository.save(
                        createProduct(name = "의류1", category = ProductCategory.APPAREL, status = ProductStatus.ACTIVE)
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

        Given("findByOwnerId 필터링 검증") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            When("ownerId=10으로 조회하면") {
                Then("[R-03] 다른 owner의 Product를 제외하고 자기 Product만 반환된다") {
                    val ownerProduct = productRepository.save(createProduct(name = "내 상품", ownerId = 10L))
                    productRepository.save(createProduct(name = "타인 상품", ownerId = 20L))

                    val results = productRepository.findByOwnerId(10L)
                    results.size shouldBe 1
                    results[0].id shouldBe ownerProduct.id
                }
            }
        }

        Given("Stock 저장 후 조회 시나리오") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            When("productId로 Stock을 조회하면") {
                Then("[R-02] 저장된 Stock을 정확히 반환한다") {
                    val product = productRepository.save(createProduct())
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
        ownerId: Long = 1L,
    ) = Product(
        name = name,
        category = category,
        price = price,
        description = "설명",
        imageUrl = "https://example.com/image.jpg",
        status = status,
        ownerId = ownerId,
    )
}
