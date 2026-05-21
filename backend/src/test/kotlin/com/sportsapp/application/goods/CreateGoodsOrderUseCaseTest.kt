package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import com.sportsapp.domain.goods.EmptyOrderException
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderStatus
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.goods.ProductInactiveException
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class CreateGoodsOrderUseCaseTest : BehaviorSpec({

    val baseItems = listOf(OrderItemInput(productId = 1L, quantity = 2))

    fun command(items: List<OrderItemInput> = baseItems, fromCart: Boolean = false) = CreateGoodsOrderCommand(
        userId = 1L,
        idempotencyKey = "idem-key-1",
        method = PaymentMethod.CREDIT_CARD,
        fromCart = fromCart,
        items = items,
    )

    fun buildPendingOrder(orderId: Long = 1L, totalAmount: BigDecimal = BigDecimal("20000")): GoodsOrder {
        val order = mockk<GoodsOrder>(relaxed = true)
        every { order.id } returns orderId
        every { order.totalAmount } returns totalAmount
        every { order.status } returns GoodsOrderStatus.PENDING
        return order
    }

    fun buildConfirmedOrder(orderId: Long = 1L, totalAmount: BigDecimal = BigDecimal("20000")): GoodsOrder {
        val order = mockk<GoodsOrder>(relaxed = true)
        every { order.id } returns orderId
        every { order.totalAmount } returns totalAmount
        every { order.status } returns GoodsOrderStatus.CONFIRMED
        return order
    }

    fun buildPayment(paymentId: Long = 10L, status: PaymentStatus = PaymentStatus.COMPLETED): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns paymentId
        every { payment.status } returns status
        return payment
    }

    Given("빈 items 목록인 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val emptyCommand = command(emptyList())

        every { goodsDomainService.createPendingOrder(1L, emptyList()) } throws EmptyOrderException()

        When("execute를 호출하면") {
            Then("[U-01] EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> { useCase.execute(emptyCommand) }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val inactiveItems = listOf(OrderItemInput(productId = 99L, quantity = 1))
        val inactiveCommand = command(inactiveItems)

        every { goodsDomainService.createPendingOrder(1L, inactiveItems) } throws ProductInactiveException(99L)

        When("execute를 호출하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> { useCase.execute(inactiveCommand) }
            }
        }
    }

    Given("유효한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val validCommand = command()
        val pendingOrder = buildPendingOrder()
        val payment = buildPayment()
        val confirmedOrder = buildConfirmedOrder()

        every { goodsDomainService.createPendingOrder(1L, baseItems) } returns pendingOrder
        every {
            paymentDomainService.create(
                userId = 1L,
                idempotencyKey = "idem-key-1",
                orderType = any(),
                orderId = 1L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("20000"),
                currency = "KRW",
            )
        } returns payment
        every { goodsDomainService.markPaid(1L, 10L) } returns confirmedOrder

        When("execute를 호출하면") {
            Then("[U-02] GoodsOrderResponse(orderId, paymentId, paymentStatus=COMPLETED)가 반환되고 markPaid가 호출된다") {
                val result = useCase.execute(validCommand)
                result.id shouldBe 1L
                result.paymentId shouldBe 10L
                result.paymentStatus shouldBe PaymentStatus.COMPLETED
                result.totalAmount shouldBe BigDecimal("20000")
                verify(exactly = 1) { goodsDomainService.markPaid(1L, 10L) }
            }
        }
    }

    Given("PG 호출이 PaymentStatus.FAILED를 반환하는 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val validCommand = command()
        val pendingOrder = buildPendingOrder()
        val failedPayment = buildPayment(status = PaymentStatus.FAILED)

        every { goodsDomainService.createPendingOrder(1L, baseItems) } returns pendingOrder
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns failedPayment
        justRun { goodsDomainService.cancelPendingOrder(1L) }

        When("execute를 호출하면") {
            Then("[U-04] 보상 트랜잭션으로 cancelPendingOrder가 호출되고 markPaid는 호출되지 않는다") {
                useCase.execute(validCommand)
                verify(exactly = 1) { goodsDomainService.cancelPendingOrder(1L) }
                verify(exactly = 0) { goodsDomainService.markPaid(any(), any()) }
            }
        }
    }

    Given("PG 호출이 PaymentStatus.PENDING 같은 예상치 못한 상태를 반환하는 경우") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val validCommand = command()
        val pendingOrder = buildPendingOrder()
        val unexpectedPayment = buildPayment(status = PaymentStatus.PENDING)

        every { goodsDomainService.createPendingOrder(1L, baseItems) } returns pendingOrder
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns unexpectedPayment
        justRun { goodsDomainService.cancelPendingOrder(1L) }

        When("execute를 호출하면") {
            Then("[U-06] markPaid가 호출되지 않고 cancelPendingOrder가 호출된다") {
                useCase.execute(validCommand)
                verify(exactly = 0) { goodsDomainService.markPaid(any(), any()) }
                verify(exactly = 1) { goodsDomainService.cancelPendingOrder(1L) }
            }
        }
    }

    Given("fromCart=true이고 결제가 성공하는 경우") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val cartDomainService = mockk<CartDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, cartDomainService)
        val cartCommand = command(fromCart = true)
        val pendingOrder = buildPendingOrder()
        val payment = buildPayment()
        val confirmedOrder = buildConfirmedOrder()

        every { goodsDomainService.createPendingOrder(1L, baseItems) } returns pendingOrder
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns payment
        every { goodsDomainService.markPaid(1L, 10L) } returns confirmedOrder
        justRun { cartDomainService.clearCart(1L) }

        When("execute를 호출하면") {
            Then("[U-05] 결제 성공 후 cartDomainService.clearCart가 호출된다") {
                useCase.execute(cartCommand)
                verify(exactly = 1) { cartDomainService.clearCart(1L) }
            }
        }
    }
})
