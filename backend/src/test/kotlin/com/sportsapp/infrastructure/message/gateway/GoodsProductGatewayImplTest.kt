package com.sportsapp.infrastructure.message.gateway

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

/**
 * `GoodsProductGateway` 구현체 — goods `ProductRepository`(내부 조회, Client 아님)로
 * `Product.ownerId`를 조회한다 (BE-11, TDD FR-18). infrastructure -> domain.goods 의존은
 * `FacilityOwnershipGatewayImpl`(booking -> facility)과 동일한 크로스 도메인 게이트웨이 패턴이다.
 */
class GoodsProductGatewayImplTest(
    @Autowired private val goodsProductGateway: GoodsProductGateway,
    @Autowired private val productJpaRepository: ProductJpaRepository,
) : BaseJpaIntegrationTest() {

    private fun seedProduct(ownerId: Long): Product = productJpaRepository.save(
        Product(
            name = "축구화",
            category = ProductCategory.FOOTWEAR,
            price = BigDecimal("30000"),
            description = "설명",
            imageUrl = "https://example.com/shoes.jpg",
            status = ProductStatus.ACTIVE,
            ownerId = ownerId,
        ),
    )

    init {
        Given("owner_id=555 인 상품이 저장된 상태") {
            val product = seedProduct(555L)

            When("findOwnerId 를 호출하면") {
                val ownerId = goodsProductGateway.findOwnerId(product.id)

                Then("Product.ownerId(555)가 반환된다") {
                    ownerId shouldBe 555L
                }
            }
        }

        Given("존재하지 않는 productId") {
            When("findOwnerId 를 호출하면") {
                Then("ResourceNotFoundException 이 발생한다") {
                    shouldThrow<ResourceNotFoundException> {
                        goodsProductGateway.findOwnerId(999_999L)
                    }
                }
            }
        }
    }
}
