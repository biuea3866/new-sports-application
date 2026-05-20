package com.sportsapp.application.goods

import com.sportsapp.domain.goods.EmptyOrderException
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.goods.OrderWithPayment
import com.sportsapp.domain.goods.OutOfStockException
import com.sportsapp.domain.goods.ProductInactiveException
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

class CreateGoodsOrderUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = CreateGoodsOrderUseCase(goodsDomainService)

    val baseItems = listOf(OrderItemInput(productId = 1L, quantity = 2))

    fun command(items: List<OrderItemInput> = baseItems) = CreateGoodsOrderCommand(
        userId = 1L,
        idempotencyKey = "idem-key-1",
        method = PaymentMethod.CREDIT_CARD,
        fromCart = false,
        items = items,
    )

    Given("빈 items 목록인 CreateGoodsOrderCommand가 주어졌을 때") {
        val emptyCommand = command(emptyList())

        every {
            goodsDomainService.createOrderWithPayment(1L, "idem-key-1", PaymentMethod.CREDIT_CARD, false, emptyList())
        } throws EmptyOrderException()

        When("execute를 호출하면") {
            Then("[U-01] EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> { useCase.execute(emptyCommand) }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 CreateGoodsOrderCommand가 주어졌을 때") {
        val inactiveItems = listOf(OrderItemInput(productId = 99L, quantity = 1))
        val inactiveCommand = command(inactiveItems)

        every {
            goodsDomainService.createOrderWithPayment(1L, "idem-key-1", PaymentMethod.CREDIT_CARD, false, inactiveItems)
        } throws ProductInactiveException(99L)

        When("execute를 호출하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> { useCase.execute(inactiveCommand) }
            }
        }
    }

    Given("유효한 CreateGoodsOrderCommand가 주어졌을 때") {
        val validCommand = command()
        val orderWithPayment = OrderWithPayment(
            orderId = 1L,
            paymentId = 10L,
            paymentStatus = PaymentStatus.COMPLETED,
            totalAmount = BigDecimal("20000"),
        )

        every {
            goodsDomainService.createOrderWithPayment(1L, "idem-key-1", PaymentMethod.CREDIT_CARD, false, baseItems)
        } returns orderWithPayment

        When("execute를 호출하면") {
            Then("[U-02] GoodsOrderResponse(orderId, paymentId, paymentStatus)가 반환된다") {
                val result = useCase.execute(validCommand)
                result.id shouldBe 1L
                result.paymentId shouldBe 10L
                result.paymentStatus shouldBe PaymentStatus.COMPLETED
                result.totalAmount shouldBe BigDecimal("20000")
            }
        }
    }
})
