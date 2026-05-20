package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartItem
import com.sportsapp.domain.goods.CartRepository
import com.sportsapp.domain.goods.CartItemRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate

class CartRepositoryTest(
    @Autowired private val cartRepository: CartRepository,
    @Autowired private val cartItemRepository: CartItemRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("Cart 저장 후 조회 검증") {
            afterEach {
                jdbcTemplate.execute("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (1, 2, 3, 999))")
                jdbcTemplate.execute("DELETE FROM carts WHERE user_id IN (1, 2, 3, 999)")
            }

            When("userId로 Cart를 저장하고 findByUserId로 조회하면") {
                Then("[R-roundtrip] 저장된 Cart가 올바르게 복원된다") {
                    val cart = cartRepository.save(Cart(userId = 1L))
                    cart.id shouldNotBe 0L

                    val found = cartRepository.findByUserId(1L)
                    found shouldNotBe null
                    found?.userId shouldBe 1L
                    found?.createdAt shouldNotBe null
                }
            }

            When("[R-01] 동일 userId로 Cart를 두 번 저장하면") {
                Then("unique 제약 위반이 발생한다") {
                    cartRepository.save(Cart(userId = 2L))
                    shouldThrow<DataIntegrityViolationException> {
                        cartRepository.save(Cart(userId = 2L))
                    }
                }
            }

            When("[R-02] Cart에 CartItem을 추가한 후 CartItem을 soft-delete하면") {
                Then("findByCartId는 삭제된 항목을 반환하지 않는다") {
                    val cart = cartRepository.save(Cart(userId = 3L))
                    val item = cartItemRepository.save(
                        CartItem(cartId = cart.id, productId = 100L, quantity = 2)
                    )

                    val beforeDelete = cartItemRepository.findByCartId(cart.id)
                    beforeDelete.size shouldBe 1

                    item.softDelete(userId = 3L)
                    cartItemRepository.save(item)

                    val afterDelete = cartItemRepository.findByCartId(cart.id)
                    afterDelete.size shouldBe 0
                }
            }

            When("동일 (cartId, productId)로 CartItem을 두 번 insert하면") {
                Then("[R-01 unique] unique 제약 위반이 발생한다") {
                    val cart = cartRepository.save(Cart(userId = 999L))
                    cartItemRepository.save(CartItem(cartId = cart.id, productId = 200L, quantity = 1))

                    shouldThrow<DataIntegrityViolationException> {
                        cartItemRepository.save(CartItem(cartId = cart.id, productId = 200L, quantity = 1))
                    }
                }
            }
        }
    }
}
