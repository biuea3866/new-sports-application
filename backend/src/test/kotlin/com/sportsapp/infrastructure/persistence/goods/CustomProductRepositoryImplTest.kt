package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.CustomProductRepository
import com.sportsapp.domain.goods.Product
import com.sportsapp.domain.goods.ProductCategory
import com.sportsapp.domain.goods.ProductStatus
import com.sportsapp.domain.goods.Stock
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class CustomProductRepositoryImplTest(
    @Autowired private val customProductRepository: CustomProductRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val stockJpaRepository: StockJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("TRUNCATE TABLE stocks")
        jdbcTemplate.execute("TRUNCATE TABLE products")
    }

    private fun saveShoeWithStock(name: String, price: BigDecimal, quantity: Int, ownerId: Long = 1L): Product {
        val product = productJpaRepository.save(
            Product(
                name = name,
                category = ProductCategory.FOOTWEAR,
                price = price,
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

        Given("[R-01] category=FOOTWEAR + keyword=러닝 조건으로 검색하면") {
            resetData()
            saveShoeWithStock("나이키 러닝화", BigDecimal("89000"), 5)
            saveShoeWithStock("아디다스 러닝화", BigDecimal("120000"), 0)
            productJpaRepository.save(
                Product(
                    name = "스포츠 반팔",
                    category = ProductCategory.APPAREL,
                    price = BigDecimal("35000"),
                    description = "쿨링",
                    imageUrl = "https://example.com/img.jpg",
                    status = ProductStatus.ACTIVE,
                    ownerId = 1L,
                )
            )

            When("검색을 실행하면") {
                Then("FOOTWEAR 카테고리의 러닝 포함 상품 2건이 반환된다") {
                    val result = customProductRepository.search(
                        category = ProductCategory.FOOTWEAR,
                        keyword = "러닝",
                        priceMin = null,
                        priceMax = null,
                        pageable = PageRequest.of(0, 20),
                    )
                    result.totalElements shouldBe 2
                    result.content.all { it.product.category == ProductCategory.FOOTWEAR } shouldBe true
                }
            }
        }

        Given("[R-01] priceMin=50000 + priceMax=100000 가격범위 조건으로 검색하면") {
            resetData()
            saveShoeWithStock("나이키 러닝화", BigDecimal("89000"), 5)
            saveShoeWithStock("아디다스 러닝화", BigDecimal("120000"), 0)

            When("검색을 실행하면") {
                Then("해당 범위의 상품 1건(89000원)만 반환된다") {
                    val result = customProductRepository.search(
                        category = null,
                        keyword = null,
                        priceMin = BigDecimal("50000"),
                        priceMax = BigDecimal("100000"),
                        pageable = PageRequest.of(0, 20),
                    )
                    result.totalElements shouldBe 1
                    result.content[0].product.price shouldBe BigDecimal("89000.00")
                }
            }
        }

        Given("[R-02] Stock fetch join 검증") {
            resetData()
            saveShoeWithStock("나이키 러닝화", BigDecimal("89000"), 5)
            val shoesProduct2 = saveShoeWithStock("아디다스 러닝화", BigDecimal("120000"), 0)

            When("FOOTWEAR 전체 조회하면") {
                Then("재고 0인 상품도 stockQuantity=0으로 응답에 포함된다") {
                    val result = customProductRepository.search(
                        category = ProductCategory.FOOTWEAR,
                        keyword = null,
                        priceMin = null,
                        priceMax = null,
                        pageable = PageRequest.of(0, 20),
                    )
                    result.totalElements shouldBe 2
                    val zeroStockItem = result.content.find { it.product.id == shoesProduct2.id }
                    zeroStockItem shouldNotBe null
                    zeroStockItem?.stockQuantity shouldBe 0
                }
            }
        }

        Given("[R-01] sort=price 옵션 검증") {
            resetData()
            saveShoeWithStock("나이키 러닝화", BigDecimal("89000"), 5)
            saveShoeWithStock("아디다스 러닝화", BigDecimal("120000"), 0)

            When("sort=price(ASC)로 검색하면") {
                Then("가격 오름차순으로 정렬된 결과가 반환된다") {
                    val result = customProductRepository.search(
                        category = ProductCategory.FOOTWEAR,
                        keyword = null,
                        priceMin = null,
                        priceMax = null,
                        pageable = PageRequest.of(0, 20, Sort.by("price")),
                    )
                    result.totalElements shouldBe 2
                    result.content[0].product.price shouldBe BigDecimal("89000.00")
                    result.content[1].product.price shouldBe BigDecimal("120000.00")
                }
            }
        }

        Given("[R-01] 페이지네이션 size=1 검증") {
            resetData()
            saveShoeWithStock("나이키 러닝화", BigDecimal("89000"), 5)
            saveShoeWithStock("아디다스 러닝화", BigDecimal("120000"), 0)

            When("size=1, page=0으로 검색하면") {
                Then("totalElements=2, content 1건, totalPages=2가 반환된다") {
                    val result = customProductRepository.search(
                        category = ProductCategory.FOOTWEAR,
                        keyword = null,
                        priceMin = null,
                        priceMax = null,
                        pageable = PageRequest.of(0, 1),
                    )
                    result.totalElements shouldBe 2
                    result.content.size shouldBe 1
                    result.totalPages shouldBe 2
                }
            }
        }

        Given("[R-03] findByOwnerId 소유자 필터링 + Stock join 검증") {
            resetData()
            saveShoeWithStock("내 러닝화", BigDecimal("89000"), 5, ownerId = 10L)
            saveShoeWithStock("내 트레일화", BigDecimal("150000"), 3, ownerId = 10L)
            saveShoeWithStock("타인 러닝화", BigDecimal("99000"), 7, ownerId = 20L)

            When("ownerId=10으로 findByOwnerId를 호출하면") {
                Then("[R-03] ownerId=10의 상품 2건만 Stock join과 함께 반환된다") {
                    val result = customProductRepository.findByOwnerId(
                        ownerId = 10L,
                        pageable = PageRequest.of(0, 20),
                    )
                    result.totalElements shouldBe 2
                    result.content.all { it.product.ownerId == 10L } shouldBe true
                    result.content.find { it.product.name == "내 러닝화" }?.stockQuantity shouldBe 5
                    result.content.find { it.product.name == "내 트레일화" }?.stockQuantity shouldBe 3
                }
            }
        }

        Given("[R-03] findByOwnerId 페이지네이션 검증") {
            resetData()
            saveShoeWithStock("내 러닝화", BigDecimal("89000"), 5, ownerId = 10L)
            saveShoeWithStock("내 트레일화", BigDecimal("150000"), 3, ownerId = 10L)
            saveShoeWithStock("타인 러닝화", BigDecimal("99000"), 7, ownerId = 20L)

            When("ownerId=10으로 size=1 페이지네이션을 호출하면") {
                Then("[R-03] totalElements=2, content 1건, totalPages=2가 반환된다") {
                    val result = customProductRepository.findByOwnerId(
                        ownerId = 10L,
                        pageable = PageRequest.of(0, 1),
                    )
                    result.totalElements shouldBe 2
                    result.content.size shouldBe 1
                    result.totalPages shouldBe 2
                }
            }
        }
    }
}
