package com.sportsapp.application.goods

import com.sportsapp.domain.goods.EmptyOrderException
import com.sportsapp.domain.goods.GoodsDomainService
import com.sportsapp.domain.goods.GoodsOrder
import com.sportsapp.domain.goods.GoodsOrderStatus
import com.sportsapp.domain.goods.OrderItemInput
import com.sportsapp.domain.goods.ProductInactiveException
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.payment.PgInitiateResult
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import org.springframework.transaction.support.TransactionTemplate

class CreateGoodsOrderUseCaseTest : BehaviorSpec({

    val baseItems = listOf(OrderItemInput(productId = 1L, quantity = 2))
    val idempotencyKey = "idem-key-1"

    fun command(items: List<OrderItemInput> = baseItems) = CreateGoodsOrderCommand(
        userId = 1L,
        idempotencyKey = idempotencyKey,
        method = PaymentMethod.CREDIT_CARD,
        fromCart = false,
        items = items,
    )

    fun buildPendingOrder(orderId: Long = 1L, totalAmount: BigDecimal = BigDecimal("20000")): GoodsOrder {
        val order = mockk<GoodsOrder>(relaxed = true)
        every { order.id } returns orderId
        every { order.totalAmount } returns totalAmount
        every { order.status } returns GoodsOrderStatus.PENDING
        every { order.paymentId } returns null
        return order
    }

    fun buildPgResult(paymentId: Long = 10L, status: PaymentStatus = PaymentStatus.READY): PgInitiateResult =
        PgInitiateResult(
            paymentId = paymentId,
            status = status,
            pgTransactionId = "tid-001",
            checkoutUrl = "http://checkout",
        )

    // TransactionTemplate mock that executes the callback immediately (no real tx)
    fun passthroughTransactionTemplate(): TransactionTemplate {
        val tt = mockk<TransactionTemplate>()
        every { tt.execute<Any>(any()) } answers {
            val callback = firstArg<org.springframework.transaction.support.TransactionCallback<Any>>()
            callback.doInTransaction(mockk(relaxed = true))
        }
        return tt
    }

    Given("유효한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, passthroughTransactionTemplate())
        val validCommand = command()
        val pendingOrder = buildPendingOrder()
        val pgResult = buildPgResult(status = PaymentStatus.READY)

        every { goodsDomainService.createPendingOrder(1L, baseItems, idempotencyKey) } returns pendingOrder
        every {
            paymentDomainService.createPending(
                userId = 1L,
                idempotencyKey = idempotencyKey,
                orderType = any(),
                orderId = 1L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("20000"),
                currency = "KRW",
            )
        } returns 10L
        every { paymentDomainService.initiatePg(any()) } returns pgResult

        When("execute를 호출하면") {
            val result = useCase.execute(validCommand)

            Then("orderId와 paymentId가 포함된 응답이 반환된다") {
                result.id shouldBe 1L
                result.paymentId shouldBe 10L
                result.totalAmount shouldBe BigDecimal("20000")
            }

            Then("PG 호출(initiatePg)은 createPendingOrder + createPending tx 이후에 호출된다") {
                verify(exactly = 1) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 1) { paymentDomainService.initiatePg(any()) }
            }

            Then("markPaid는 호출되지 않는다") {
                verify(exactly = 0) { goodsDomainService.markPaid(any(), any()) }
            }

            Then("cancelPendingOrder는 호출되지 않는다") {
                verify(exactly = 0) { goodsDomainService.cancelPendingOrder(any()) }
            }
        }
    }

    Given("동일 idempotencyKey로 이미 CONFIRMED 상태의 주문이 존재할 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, passthroughTransactionTemplate())
        val validCommand = command()

        val existingOrder = mockk<GoodsOrder>(relaxed = true)
        every { existingOrder.id } returns 5L
        every { existingOrder.totalAmount } returns BigDecimal("20000")
        every { existingOrder.status } returns GoodsOrderStatus.CONFIRMED
        every { existingOrder.paymentId } returns 55L
        every { goodsDomainService.createPendingOrder(1L, baseItems, idempotencyKey) } returns existingOrder

        When("execute를 호출하면") {
            val result = useCase.execute(validCommand)

            Then("기존 주문을 그대로 반환하고 결제를 재생성하지 않는다") {
                result.id shouldBe 5L
                result.totalAmount shouldBe BigDecimal("20000")
                result.paymentId shouldBe 55L
                verify(exactly = 0) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { paymentDomainService.initiatePg(any()) }
            }
        }
    }

    Given("빈 items 목록인 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, passthroughTransactionTemplate())
        val emptyCommand = command(emptyList())

        every { goodsDomainService.createPendingOrder(1L, emptyList(), idempotencyKey) } throws EmptyOrderException()

        When("execute를 호출하면") {
            Then("EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> { useCase.execute(emptyCommand) }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, passthroughTransactionTemplate())
        val inactiveItems = listOf(OrderItemInput(productId = 99L, quantity = 1))
        val inactiveCommand = command(inactiveItems)

        every { goodsDomainService.createPendingOrder(1L, inactiveItems, idempotencyKey) } throws ProductInactiveException(99L)

        When("execute를 호출하면") {
            Then("ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> { useCase.execute(inactiveCommand) }
            }
        }
    }
})
