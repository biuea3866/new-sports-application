package com.sportsapp.infrastructure.goods.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.repository.CartRepository
import com.sportsapp.domain.goods.repository.CartItemRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

class CartRepositoryTest(
    @Autowired private val cartRepository: CartRepository,
    @Autowired private val cartItemRepository: CartItemRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        Given("Cart 저장 후 조회 검증") {
            afterEach {
                jdbcTemplate.execute("DELETE FROM cart_items WHERE cart_id IN (SELECT id FROM carts WHERE user_id IN (1, 2, 3, 999, 5000))")
                jdbcTemplate.execute("DELETE FROM carts WHERE user_id IN (1, 2, 3, 999, 5000)")
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

            When("[R-01] soft-delete된 Cart와 동일 userId로 새 Cart를 저장하면") {
                Then("unique 제약 위반 없이 저장된다") {
                    val cart = cartRepository.save(Cart(userId = 2L))
                    cart.softDelete(userId = 2L)
                    cartRepository.save(cart)

                    val newCart = cartRepository.save(Cart(userId = 2L))
                    newCart.id shouldNotBe cart.id
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

            When("soft-delete된 CartItem과 동일 (cartId, productId)로 새 CartItem을 insert하면") {
                Then("[R-01 unique] unique 제약 위반 없이 저장된다") {
                    val cart = cartRepository.save(Cart(userId = 999L))
                    val item = cartItemRepository.save(CartItem(cartId = cart.id, productId = 200L, quantity = 1))
                    item.softDelete(userId = 999L)
                    cartItemRepository.save(item)

                    val newItem = cartItemRepository.save(CartItem(cartId = cart.id, productId = 200L, quantity = 2))
                    newItem.id shouldNotBe item.id
                }
            }

            When("[DEF-003] soft-delete된 cart가 존재하고 새 cart가 생성된 상태에서 findByUserId를 호출하면") {
                Then("500 없이 활성(deletedAt=null) cart 단일 건을 반환한다") {
                    val deletedCart = cartRepository.save(Cart(userId = 5000L))
                    deletedCart.softDelete(userId = 5000L)
                    cartRepository.save(deletedCart)

                    val activeCart = cartRepository.save(Cart(userId = 5000L))

                    val found = cartRepository.findByUserId(5000L)
                    found shouldNotBe null
                    found?.id shouldBe activeCart.id
                    found?.isDeleted shouldBe false
                }
            }

            When("[DEF-003-unique] 활성 cart가 존재하는 상태에서 findByUserId를 재호출하면") {
                Then("동일한 단일 활성 cart를 반환하며 중복 생성되지 않는다") {
                    val firstCart = cartRepository.save(Cart(userId = 5000L))

                    // getOrCreateCart 멱등성: 이미 존재하면 재사용
                    val found = cartRepository.findByUserId(5000L)
                    found shouldNotBe null
                    found?.id shouldBe firstCart.id
                }
            }
        }
    }
}
