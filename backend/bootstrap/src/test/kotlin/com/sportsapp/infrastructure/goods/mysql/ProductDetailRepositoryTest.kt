package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.entity.ProductStatus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

class ProductDetailRepositoryTest(
    @Autowired private val productRepository: ProductRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("활성 상품과 soft-delete 상품이 각각 저장된 상태") {
            afterEach {
                jdbcTemplate.execute("TRUNCATE TABLE stocks")
                jdbcTemplate.execute("TRUNCATE TABLE products")
            }

            When("[R-01] 활성 상품 id로 findByIdAndDeletedAtIsNull 조회 시") {
                Then("상품이 반환된다") {
                    val product = productRepository.save(
                        Product(
                            name = "테니스 라켓",
                            category = ProductCategory.EQUIPMENT,
                            price = BigDecimal("50000"),
                            description = "설명",
                            imageUrl = "https://example.com/img.jpg",
                            status = ProductStatus.ACTIVE,
                            ownerId = 1L,
                        )
                    )

                    val found = productRepository.findByIdAndDeletedAtIsNull(product.id)
                    found shouldNotBe null
                    found?.name shouldBe "테니스 라켓"
                }
            }

            When("[R-02] soft-delete된 상품 id로 findByIdAndDeletedAtIsNull 조회 시") {
                Then("null이 반환된다") {
                    val product = productRepository.save(
                        Product(
                            name = "삭제된 상품",
                            category = ProductCategory.EQUIPMENT,
                            price = BigDecimal("30000"),
                            description = "설명",
                            imageUrl = "https://example.com/img.jpg",
                            status = ProductStatus.ACTIVE,
                            ownerId = 1L,
                        )
                    )
                    product.softDelete(null)
                    productRepository.save(product)

                    val found = productRepository.findByIdAndDeletedAtIsNull(product.id)
                    found shouldBe null
                }
            }
        }
    }
}
