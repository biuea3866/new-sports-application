package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.exception.EmptyOrderException
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.vo.OrderItemInput
import com.sportsapp.domain.goods.exception.ProductInactiveException
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.entity.PaymentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.application.goods.dto.CreateGoodsOrderCommand
import org.springframework.transaction.support.TransactionTemplate

class CreateGoodsOrderUseCaseTest : BehaviorSpec({

    val baseItems = listOf(OrderItemInput(productId = 1L, quantity = 2))
    val idempotencyKey = "idem-key-1"

    fun command(items: List<OrderItemInput> = baseItems, fromCart: Boolean = false) = CreateGoodsOrderCommand(
        userId = 1L,
        idempotencyKey = idempotencyKey,
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

    fun buildTransactionTemplate(order: GoodsOrder, paymentId: Long = 10L): TransactionTemplate {
        val tx = mockk<TransactionTemplate>()
        every { tx.execute<Pair<GoodsOrder, Long>>(any()) } returns (order to paymentId)
        return tx
    }

    Given("빈 items 목록인 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val pendingOrder = buildPendingOrder()
        val transactionTemplate = buildTransactionTemplate(pendingOrder)
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, transactionTemplate)
        val emptyCommand = command(emptyList())

        every { transactionTemplate.execute<Pair<GoodsOrder, Long>>(any()) } throws EmptyOrderException()

        When("execute를 호출하면") {
            Then("[U-01] EmptyOrderException이 발생한다") {
                shouldThrow<EmptyOrderException> { useCase.execute(emptyCommand) }
            }
        }
    }

    Given("INACTIVE 상품을 포함한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val pendingOrder = buildPendingOrder()
        val transactionTemplate = buildTransactionTemplate(pendingOrder)
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, transactionTemplate)
        val inactiveCommand = command(listOf(OrderItemInput(productId = 99L, quantity = 1)))

        every { transactionTemplate.execute<Pair<GoodsOrder, Long>>(any()) } throws ProductInactiveException(99L)

        When("execute를 호출하면") {
            Then("[U-03] ProductInactiveException이 발생한다") {
                shouldThrow<ProductInactiveException> { useCase.execute(inactiveCommand) }
            }
        }
    }

    Given("유효한 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val pendingOrder = buildPendingOrder()
        val transactionTemplate = buildTransactionTemplate(pendingOrder, paymentId = 10L)
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, transactionTemplate)
        val validCommand = command()

        every {
            paymentDomainService.initiatePg(any())
        } returns PgInitiateResult(paymentId = 10L, status = PaymentStatus.COMPLETED, pgTransactionId = null, checkoutUrl = null)

        When("execute를 호출하면") {
            Then("[U-02] GoodsOrderResponse(orderId, paymentId, paymentStatus=COMPLETED)가 반환된다") {
                val result = useCase.execute(validCommand)
                result.orderId shouldBe 1L
                result.paymentId shouldBe 10L
                result.paymentStatus shouldBe PaymentStatus.COMPLETED
                result.totalAmount shouldBe BigDecimal("20000")
            }
        }
    }

    Given("PG 호출이 PaymentStatus.FAILED를 반환하는 CreateGoodsOrderCommand가 주어졌을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val pendingOrder = buildPendingOrder()
        val transactionTemplate = buildTransactionTemplate(pendingOrder, paymentId = 10L)
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, transactionTemplate)
        val validCommand = command()

        every {
            paymentDomainService.initiatePg(any())
        } returns PgInitiateResult(paymentId = 10L, status = PaymentStatus.FAILED, pgTransactionId = null, checkoutUrl = null)

        When("execute를 호출하면") {
            Then("[U-04] FAILED 상태의 OrderWithPayment가 반환된다") {
                val result = useCase.execute(validCommand)
                result.paymentStatus shouldBe PaymentStatus.FAILED
            }
        }
    }

    Given("이미 CONFIRMED 상태인 주문의 멱등 재요청") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val confirmedOrder = buildConfirmedOrder()
        val transactionTemplate = buildTransactionTemplate(confirmedOrder, paymentId = 5L)
        val useCase = CreateGoodsOrderUseCase(goodsDomainService, paymentDomainService, transactionTemplate)
        val validCommand = command()
        every { confirmedOrder.paymentId } returns 5L

        When("execute를 호출하면") {
            Then("[U-05] initiatePg 없이 기존 orderId/paymentId를 반환한다") {
                val result = useCase.execute(validCommand)
                result.orderId shouldBe 1L
                result.paymentId shouldBe 5L
                verify(exactly = 0) { paymentDomainService.initiatePg(any()) }
            }
        }
    }
})
