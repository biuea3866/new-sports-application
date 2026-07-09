package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.vo.ProductCategory
import com.sportsapp.domain.goods.vo.SellerType
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal

/**
 * order 통합조회(BE-08 예정)용 GoodsOrder+상품명 조인 읽기([GoodsOrderCustomRepository.findBy]) 검증.
 * TDD "주문 표시명 확보 방식" — goods_orders → goods_order_items → products 조인(동일 컨텍스트).
 */
class GoodsOrderCustomRepositoryImplTest(
    @Autowired private val goodsOrderCustomRepository: GoodsOrderCustomRepository,
    @Autowired private val goodsOrderJpaRepository: GoodsOrderJpaRepository,
    @Autowired private val goodsOrderItemJpaRepository: GoodsOrderItemJpaRepository,
    @Autowired private val productJpaRepository: ProductJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun resetData() {
        jdbcTemplate.execute("DELETE FROM goods_order_items")
        jdbcTemplate.execute("DELETE FROM goods_orders")
        jdbcTemplate.execute("DELETE FROM products")
    }

    private fun saveProduct(name: String): Product =
        productJpaRepository.save(
            Product(
                name = name,
                category = ProductCategory.FOOTWEAR,
                price = BigDecimal("89000"),
                description = "설명",
                imageUrl = "https://example.com/img.jpg",
                status = ProductStatus.ACTIVE,
                sellerType = SellerType.B2C,
                ownerId = 1L,
            )
        )

    init {
        afterEach { resetData() }

        Given("단일 상품 1건으로 구성된 주문이 있는 상황") {
            resetData()
            val product = saveProduct("나이키 러닝화")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("89000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = order.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("89000"))
            )

            When("findBy(userId)를 호출하면") {
                Then("title이 상품명 그대로 반환된다") {
                    val page = goodsOrderCustomRepository.findBy(1L, PageRequest.of(0, 20))
                    page.totalElements shouldBe 1
                    page.content[0].title shouldBe "나이키 러닝화"
                }
            }
        }

        Given("서로 다른 상품 3건으로 구성된 주문이 있는 상황") {
            resetData()
            val product1 = saveProduct("나이키 러닝화")
            val product2 = saveProduct("아디다스 반바지")
            val product3 = saveProduct("스포츠 양말")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 2L, totalAmount = BigDecimal("150000")))
            goodsOrderItemJpaRepository.saveAll(
                listOf(
                    GoodsOrderItem(orderId = order.id, productId = product1.id, quantity = 1, unitPrice = BigDecimal("89000")),
                    GoodsOrderItem(orderId = order.id, productId = product2.id, quantity = 1, unitPrice = BigDecimal("35000")),
                    GoodsOrderItem(orderId = order.id, productId = product3.id, quantity = 1, unitPrice = BigDecimal("26000")),
                )
            )

            When("findBy(userId)를 호출하면") {
                Then("대표 상품명 + 외 N건으로 title이 구성된다") {
                    val page = goodsOrderCustomRepository.findBy(2L, PageRequest.of(0, 20))
                    page.totalElements shouldBe 1
                    page.content[0].title shouldBe "나이키 러닝화 외 2건"
                }
            }
        }

        Given("참조 Product가 삭제된 주문이 있는 상황(엣지)") {
            resetData()
            val product = saveProduct("삭제될 상품")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 3L, totalAmount = BigDecimal("50000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = order.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("50000"))
            )
            jdbcTemplate.update("UPDATE products SET deleted_at = NOW(6) WHERE id = ?", product.id)

            When("findBy(userId)를 호출하면") {
                Then("빈 title로 방어 반환한다") {
                    val page = goodsOrderCustomRepository.findBy(3L, PageRequest.of(0, 20))
                    page.totalElements shouldBe 1
                    page.content[0].title shouldBe ""
                }
            }
        }

        Given("다른 userId의 주문이 섞여 있는 상황") {
            resetData()
            val product = saveProduct("나이키 러닝화")
            val myOrder = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 10L, totalAmount = BigDecimal("89000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = myOrder.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("89000"))
            )
            val otherOrder = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 20L, totalAmount = BigDecimal("10000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = otherOrder.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("10000"))
            )

            When("userId=10으로 findBy를 호출하면") {
                Then("userId=10의 주문 1건만 반환된다") {
                    val page = goodsOrderCustomRepository.findBy(10L, PageRequest.of(0, 20))
                    page.totalElements shouldBe 1
                    page.content.all { it.order.userId == 10L } shouldBe true
                }
            }
        }

        Given("단일 상품 1건으로 구성된 주문 상세를 조회하는 상황") {
            resetData()
            val product = saveProduct("나이키 러닝화")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 30L, totalAmount = BigDecimal("89000")))
            goodsOrderItemJpaRepository.save(
                GoodsOrderItem(orderId = order.id, productId = product.id, quantity = 1, unitPrice = BigDecimal("89000"))
            )

            When("findTitleFor(orderId)를 호출하면") {
                Then("findBy와 동일한 buildTitle 로직으로 상품명 그대로 반환한다") {
                    goodsOrderCustomRepository.findTitleFor(order.id) shouldBe "나이키 러닝화"
                }
            }
        }

        Given("서로 다른 상품 2건으로 구성된 주문 상세를 조회하는 상황") {
            resetData()
            val product1 = saveProduct("나이키 러닝화")
            val product2 = saveProduct("아디다스 반바지")
            val order = goodsOrderJpaRepository.save(GoodsOrder.create(userId = 31L, totalAmount = BigDecimal("124000")))
            goodsOrderItemJpaRepository.saveAll(
                listOf(
                    GoodsOrderItem(orderId = order.id, productId = product1.id, quantity = 1, unitPrice = BigDecimal("89000")),
                    GoodsOrderItem(orderId = order.id, productId = product2.id, quantity = 1, unitPrice = BigDecimal("35000")),
                )
            )

            When("findTitleFor(orderId)를 호출하면") {
                Then("대표 상품명 + 외 N건으로 title이 구성된다") {
                    goodsOrderCustomRepository.findTitleFor(order.id) shouldBe "나이키 러닝화 외 1건"
                }
            }
        }

        Given("존재하지 않는 orderId로 주문 상세를 조회하는 상황(엣지)") {
            resetData()

            When("findTitleFor(orderId)를 호출하면") {
                Then("빈 title로 방어 반환한다") {
                    goodsOrderCustomRepository.findTitleFor(999999L) shouldBe ""
                }
            }
        }
    }
}
