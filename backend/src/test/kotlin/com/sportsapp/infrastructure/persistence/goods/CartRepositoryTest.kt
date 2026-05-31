package com.sportsapp.infrastructure.persistence.goods

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.goods.Cart
import com.sportsapp.domain.goods.CartItem
import com.sportsapp.domain.goods.CartRepository
import com.sportsapp.domain.goods.CartItemRepository
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

            When("userId로 Cart를 저장하고 findActiveByUserId로 조회하면") {
                Then("저장된 Cart가 올바르게 복원된다") {
                    val cart = cartRepository.save(Cart(userId = 1L))
                    cart.id shouldNotBe 0L

                    val found = cartRepository.findActiveByUserId(1L)
                    found shouldNotBe null
                    found?.userId shouldBe 1L
                    found?.createdAt shouldNotBe null
                }
            }

            When("soft-delete된 Cart가 존재하는 userId로 findActiveByUserId를 호출하면") {
                Then("deletedAt이 null인 Cart만 반환된다") {
                    val cart = cartRepository.save(Cart(userId = 2L))
                    cart.softDelete(userId = 2L)
                    cartRepository.save(cart)

                    val found = cartRepository.findActiveByUserId(2L)
                    found shouldBe null
                }
            }

            When("soft-delete된 Cart와 동일 userId로 새 Cart를 저장하면") {
                Then("unique 제약 위반 없이 저장되고 findActiveByUserId는 새 Cart를 반환한다") {
                    val deletedCart = cartRepository.save(Cart(userId = 2L))
                    deletedCart.softDelete(userId = 2L)
                    cartRepository.save(deletedCart)

                    val newCart = cartRepository.save(Cart(userId = 2L))
                    newCart.id shouldNotBe deletedCart.id

                    val found = cartRepository.findActiveByUserId(2L)
                    found shouldNotBe null
                    found?.id shouldBe newCart.id
                    found?.isDeleted shouldBe false
                }
            }

            When("Cart에 CartItem을 추가한 후 CartItem을 soft-delete하면") {
                Then("findByCartId는 삭제된 항목을 반환하지 않는다") {
                    val cart = cartRepository.save(Cart(userId = 3L))
                    val item = cartItemRepository.save(
                        CartItem(cart = cart, productId = 100L, quantity = 2)
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
                Then("unique 제약 위반 없이 저장된다") {
                    val cart = cartRepository.save(Cart(userId = 999L))
                    val item = cartItemRepository.save(CartItem(cart = cart, productId = 200L, quantity = 1))
                    item.softDelete(userId = 999L)
                    cartItemRepository.save(item)

                    val newItem = cartItemRepository.save(CartItem(cart = cart, productId = 200L, quantity = 2))
                    newItem.id shouldNotBe item.id
                }
            }

            When("soft-delete된 cart가 존재하고 새 cart가 생성된 상태에서 findActiveByUserId를 호출하면") {
                Then("활성(deletedAt=null) cart 단일 건을 반환한다") {
                    val deletedCart = cartRepository.save(Cart(userId = 5000L))
                    deletedCart.softDelete(userId = 5000L)
                    cartRepository.save(deletedCart)

                    val activeCart = cartRepository.save(Cart(userId = 5000L))

                    val found = cartRepository.findActiveByUserId(5000L)
                    found shouldNotBe null
                    found?.id shouldBe activeCart.id
                    found?.isDeleted shouldBe false
                }
            }

            When("활성 cart가 존재하는 상태에서 findActiveByUserId를 재호출하면") {
                Then("동일한 단일 활성 cart를 반환하며 중복 생성되지 않는다") {
                    val firstCart = cartRepository.save(Cart(userId = 5000L))

                    val found = cartRepository.findActiveByUserId(5000L)
                    found shouldNotBe null
                    found?.id shouldBe firstCart.id
                }
            }
        }
    }
}
