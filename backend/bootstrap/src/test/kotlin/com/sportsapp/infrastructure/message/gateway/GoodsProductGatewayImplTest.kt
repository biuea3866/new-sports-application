package com.sportsapp.infrastructure.message.gateway

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.message.gateway.GoodsProductGateway
import com.sportsapp.infrastructure.goods.mysql.ProductJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal

/**
 * `GoodsProductGateway` 구현체 — goods 의 공개 행위 계약인 `GoodsDomainService.findOwnerIdBy` 를
 * 경유해 `Product.ownerId` 를 조회한다 (BE-11, TDD FR-18). goods 의 `ProductRepository`(=`products`
 * 테이블)를 직접 알지 않는다. infrastructure -> domain.goods 의존은 `FacilityOwnershipGatewayImpl`
 * (booking -> facility)과 동일한 크로스 도메인 게이트웨이 패턴이다.
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

        Given("소프트 삭제된 상품이 존재할 때") {
            val product = seedProduct(777L)
            product.softDelete(null)
            productJpaRepository.save(product)

            When("findOwnerId 를 호출하면") {
                Then("삭제된 상품은 없는 것으로 취급해 ResourceNotFoundException 이 발생한다") {
                    shouldThrow<ResourceNotFoundException> {
                        goodsProductGateway.findOwnerId(product.id)
                    }
                }
            }
        }

        Given("GoodsProductGatewayImpl 의 생성자 의존") {
            When("선언된 필드 타입을 검사하면") {
                Then("ProductRepository 타입 의존이 남아있지 않다 (GoodsDomainService 경유 전환)") {
                    val fieldTypeNames = GoodsProductGatewayImpl::class.java.declaredFields
                        .map { it.type.name }
                    fieldTypeNames shouldNotContain "com.sportsapp.domain.goods.repository.ProductRepository"
                }
            }
        }
    }
}
