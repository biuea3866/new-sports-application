package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.TicketOrder
import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class PurchaseTicketsUseCaseTest : BehaviorSpec({

    val lockId = "1:10,1:20"
    val userId = 7L
    val idempotencyKey = "idem-purchase-01"

    fun buildCommand(overrideLockId: String = lockId) = PurchaseTicketsCommand(
        userId = userId,
        lockId = overrideLockId,
        idempotencyKey = idempotencyKey,
        method = PaymentMethod.CREDIT_CARD,
        currency = "KRW",
    )

    fun buildPendingOrder(orderId: Long = 100L): TicketOrder {
        val order = mockk<TicketOrder>(relaxed = true)
        every { order.id } returns orderId
        every { order.status } returns OrderStatus.PENDING
        return order
    }

    fun buildPayment(status: PaymentStatus): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns 999L
        every { payment.status } returns status
        return payment
    }

    Given("본인 락이 아닌 좌석 구매 요청") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } throws SeatNotLockOwnerException(1L, 10L)

        When("[U-01] useCase.execute를 호출하면") {
            Then("SeatNotLockOwnerException이 던져지고 TicketOrder가 생성되지 않는다") {
                shouldThrow<SeatNotLockOwnerException> {
                    useCase.execute(buildCommand())
                }
                verify(exactly = 0) { ticketingDomainService.createPendingOrder(any(), any()) }
            }
        }
    }

    Given("락 검증 성공 + PG FAILED 응답 (PaymentStatus.FAILED)") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrder = buildPendingOrder()
        val failedPayment = buildPayment(PaymentStatus.FAILED)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("50000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrder
        every {
            paymentDomainService.create(
                userId = userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("50000"),
                currency = "KRW",
            )
        } returns failedPayment
        justRun { ticketingDomainService.cancelOrder(100L) }

        When("[U-02] PG가 FAILED 상태를 반환하면") {
            useCase.execute(buildCommand())

            Then("ticketingDomainService.cancelOrder(orderId)가 호출된다") {
                verify(exactly = 1) { ticketingDomainService.cancelOrder(100L) }
            }
        }
    }

    Given("락 검증 성공 + 정상 결제") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrder = buildPendingOrder()
        val completedPayment = buildPayment(PaymentStatus.COMPLETED)
        val confirmedOrder = mockk<TicketOrder>(relaxed = true).also {
            every { it.id } returns 100L
            every { it.status } returns OrderStatus.CONFIRMED
        }

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("80000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrder
        every {
            paymentDomainService.create(
                userId = userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("80000"),
                currency = "KRW",
            )
        } returns completedPayment
        every { ticketingDomainService.confirmOrder(100L, 999L) } returns confirmedOrder

        When("[U-03] totalAmount가 서버 계산값을 사용하는 경우") {
            val result = useCase.execute(buildCommand())

            Then("TicketOrderResponse에 ticketOrderId와 CONFIRMED 상태가 포함되고 confirmOrder가 호출된다") {
                result.ticketOrderId shouldBe 100L
                result.status shouldBe OrderStatus.CONFIRMED
                verify(exactly = 1) { ticketingDomainService.calculateAmount(lockId) }
                verify(exactly = 1) { ticketingDomainService.confirmOrder(100L, 999L) }
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("락 검증 성공 + PG FAILED 응답") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrder = buildPendingOrder()
        val failedPayment = buildPayment(PaymentStatus.FAILED)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("60000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrder
        every {
            paymentDomainService.create(
                userId = userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = 100L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("60000"),
                currency = "KRW",
            )
        } returns failedPayment
        justRun { ticketingDomainService.cancelOrder(100L) }

        When("[U-04] PG가 FAILED 상태를 반환하면") {
            useCase.execute(buildCommand())

            Then("ticketingDomainService.cancelOrder(orderId)가 호출된다") {
                verify(exactly = 1) { ticketingDomainService.cancelOrder(100L) }
            }
        }
    }

    Given("PG가 PaymentStatus.REFUNDED 같은 예상치 못한 상태를 반환하는 경우") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrder = buildPendingOrder()
        val unexpectedPayment = buildPayment(PaymentStatus.REFUNDED)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("50000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrder
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns unexpectedPayment
        justRun { ticketingDomainService.cancelOrder(100L) }

        When("[U-05] 예상치 못한 PaymentStatus가 반환되면") {
            useCase.execute(buildCommand())

            Then("confirmOrder는 호출되지 않고 cancelOrder가 호출된다") {
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
                verify(exactly = 1) { ticketingDomainService.cancelOrder(100L) }
            }
        }
    }

    Given("빈 lockId로 PurchaseTicketsCommand 생성") {
        When("[Self-Validation] lockId가 blank인 경우") {
            Then("IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    PurchaseTicketsCommand(
                        userId = 1L,
                        lockId = "",
                        idempotencyKey = "key",
                        method = PaymentMethod.CREDIT_CARD,
                        currency = "KRW",
                    )
                }
            }
        }
    }
})
