package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.service.CartDomainService
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.exception.InvalidQuantityException
import com.sportsapp.domain.goods.exception.ProductInactiveException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import com.sportsapp.application.goods.dto.AddCartItemCommand

class AddCartItemUseCaseTest : BehaviorSpec({

    val cartDomainService = mockk<CartDomainService>()
    val useCase = AddCartItemUseCase(cartDomainService)

    Given("quantity가 0인 AddCartItemCommand가 주어졌을 때") {
        val command = AddCartItemCommand(userId = 1L, productId = 10L, quantity = 0)

        every {
            cartDomainService.addItem(command.userId, command.productId, command.quantity)
        } throws InvalidQuantityException(0)

        When("execute를 호출하면") {
            Then("[U-01] InvalidQuantityException이 발생한다") {
                shouldThrow<InvalidQuantityException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("INACTIVE 상품에 대한 AddCartItemCommand가 주어졌을 때") {
        val command = AddCartItemCommand(userId = 1L, productId = 20L, quantity = 1)

        every {
            cartDomainService.addItem(command.userId, command.productId, command.quantity)
        } throws ProductInactiveException(20L)

        When("execute를 호출하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("유효한 AddCartItemCommand가 주어졌을 때") {
        val cart = Cart(userId = 1L)
        val cartItem = CartItem(cartId = 0L, productId = 10L, quantity = 2)
        val command = AddCartItemCommand(userId = 1L, productId = 10L, quantity = 2)

        every {
            cartDomainService.addItem(command.userId, command.productId, command.quantity)
        } returns (cart to listOf(cartItem))

        When("execute를 호출하면") {
            Then("[U-happy] Cart와 CartItem 목록이 반환된다") {
                val (cart, items) = useCase.execute(command)
                cart.userId shouldBe 1L
                items.size shouldBe 1
                items[0].productId shouldBe 10L
                items[0].quantity shouldBe 2
            }
        }
    }
})
