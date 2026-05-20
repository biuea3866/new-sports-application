package com.sportsapp.application.goods

import com.sportsapp.domain.goods.EmptyOrderException
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderStatus
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.goods.OutOfStockException
import com.sportsapp.domain.goods.ProductInactiveException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

class CreateGoodsOrderUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = CreateGoodsOrderUseCase(goodsDomainService)

    Given("빈 items 목록인 CreateGoodsOrderCommand가 주어졌을 때") {
        val command = CreateGoodsOrderCommand(userId = 1L, items = emptyList())

        every { goodsDomainService.createPendingOrder(1L, emptyList()) } throws EmptyOrderException()

        When("execute를 호출하면") {
            Then("[U-01] EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> { useCase.execute(command) }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 CreateGoodsOrderCommand가 주어졌을 때") {
        val items = listOf(OrderItemInput(productId = 99L, quantity = 1))
        val command = CreateGoodsOrderCommand(userId = 1L, items = items)

        every { goodsDomainService.createPendingOrder(1L, items) } throws ProductInactiveException(99L)

        When("execute를 호출하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> { useCase.execute(command) }
            }
        }
    }

    Given("유효한 CreateGoodsOrderCommand가 주어졌을 때") {
        val items = listOf(OrderItemInput(productId = 1L, quantity = 2))
        val command = CreateGoodsOrderCommand(userId = 1L, items = items)
        val order = GoodsOrder.create(1L, BigDecimal("20000"))

        every { goodsDomainService.createPendingOrder(1L, items) } returns order

        When("execute를 호출하면") {
            Then("[U-02] orderId(Long)가 반환된다") {
                val result = useCase.execute(command)
                result shouldBe order.id
            }
        }
    }
})
