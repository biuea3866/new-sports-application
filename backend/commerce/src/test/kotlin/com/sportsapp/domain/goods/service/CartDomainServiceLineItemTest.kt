package com.sportsapp.domain.goods.service

import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.entity.Product
import com.sportsapp.domain.goods.entity.ProductStatus
import com.sportsapp.domain.goods.repository.CartItemRepository
import com.sportsapp.domain.goods.repository.CartRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import com.sportsapp.domain.goods.vo.ProductCategory
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

/**
 * 장바구니 조회가 상품명·단가를 함께 돌려주는지 검증한다.
 *
 * 회귀 배경: 장바구니 항목이 상품명 대신 "상품 #121"·"상품 #122"로 렌더됐다
 * (유즈케이스 캡쳐 12-장바구니). 장바구니 응답이 productId·quantity 만 담아 화면이 상품명을
 * 알 수 없던 것이 원인이다. 같은 goods 컨텍스트의 Product 를 조인해 사람이 읽는 이름을 채운다.
 */
class CartDomainServiceLineItemTest : BehaviorSpec({

    fun productMock(productId: Long, productName: String, price: BigDecimal) = mockk<Product> {
        every { id } returns productId
        every { name } returns productName
        every { this@mockk.price } returns price
        every { imageUrl } returns "https://cdn.example.com/$productId.jpg"
        every { category } returns ProductCategory.EQUIPMENT
        every { status } returns ProductStatus.ACTIVE
    }

    Given("두 상품이 담긴 장바구니가 있는 상황") {
        val cartRepository = mockk<CartRepository>()
        val cartItemRepository = mockk<CartItemRepository>()
        val productRepository = mockk<ProductRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = CartDomainService(
            cartRepository = cartRepository,
            cartItemRepository = cartItemRepository,
            productRepository = productRepository,
            stockRepository = stockRepository,
        )

        val cart = Cart(userId = 3L)
        every { cartRepository.findByUserId(3L) } returns cart
        every { cartItemRepository.findByCartId(cart.id) } returns listOf(
            CartItem(cartId = cart.id, productId = 121L, quantity = 1),
            CartItem(cartId = cart.id, productId = 122L, quantity = 2),
        )
        every { productRepository.findById(121L) } returns
            productMock(121L, "실내 클라이밍 초크백", BigDecimal("29000"))
        every { productRepository.findById(122L) } returns
            productMock(122L, "카본 배드민턴 라켓", BigDecimal("119000"))

        When("장바구니를 조회하면") {
            val (_, lineItems) = service.getCartWithLineItems(3L)

            Then("항목마다 사람이 읽는 상품명이 채워진다") {
                lineItems.map { it.productName } shouldBe
                    listOf("실내 클라이밍 초크백", "카본 배드민턴 라켓")
            }

            Then("단가와 소계가 수량을 반영해 계산된다") {
                lineItems[1].unitPrice shouldBe BigDecimal("119000")
                lineItems[1].subtotal shouldBe BigDecimal("238000")
            }

            Then("productId 는 그대로 유지된다") {
                lineItems.map { it.productId } shouldBe listOf(121L, 122L)
            }
        }
    }

    Given("상품이 삭제돼 조회되지 않는 항목이 담긴 장바구니가 있는 상황") {
        val cartRepository = mockk<CartRepository>()
        val cartItemRepository = mockk<CartItemRepository>()
        val productRepository = mockk<ProductRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = CartDomainService(
            cartRepository = cartRepository,
            cartItemRepository = cartItemRepository,
            productRepository = productRepository,
            stockRepository = stockRepository,
        )

        val cart = Cart(userId = 4L)
        every { cartRepository.findByUserId(4L) } returns cart
        every { cartItemRepository.findByCartId(cart.id) } returns listOf(
            CartItem(cartId = cart.id, productId = 999L, quantity = 1),
        )
        every { productRepository.findById(999L) } returns null

        When("장바구니를 조회하면") {
            val (_, lineItems) = service.getCartWithLineItems(4L)

            Then("목록 전체가 실패하지 않고 기본 문구로 방어된다") {
                lineItems.single().productName shouldBe CartDomainService.UNKNOWN_PRODUCT_NAME
                lineItems.single().unitPrice shouldBe BigDecimal.ZERO
            }
        }
    }

    Given("장바구니가 비어 있는 상황") {
        val cartRepository = mockk<CartRepository>()
        val cartItemRepository = mockk<CartItemRepository>()
        val productRepository = mockk<ProductRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = CartDomainService(
            cartRepository = cartRepository,
            cartItemRepository = cartItemRepository,
            productRepository = productRepository,
            stockRepository = stockRepository,
        )

        val cart = Cart(userId = 5L)
        every { cartRepository.findByUserId(5L) } returns cart
        every { cartItemRepository.findByCartId(cart.id) } returns emptyList()

        When("장바구니를 조회하면") {
            val (_, lineItems) = service.getCartWithLineItems(5L)

            Then("빈 목록을 돌려주고 상품을 조회하지 않는다") {
                lineItems shouldBe emptyList()
            }
        }
    }
})
