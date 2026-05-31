package com.sportsapp.application.ticketing

import com.sportsapp.domain.payment.OrderType
import com.sportsapp.domain.payment.Payment
import com.sportsapp.domain.payment.PaymentDomainService
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.domain.payment.PaymentStatus
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
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

    fun buildPendingOrderResponse(orderId: Long = 100L): TicketOrderResponse =
        TicketOrderResponse(ticketOrderId = orderId, status = OrderStatus.PENDING)

    fun buildPayment(status: PaymentStatus, paymentId: Long = 999L): Payment {
        val payment = mockk<Payment>()
        every { payment.id } returns paymentId
        every { payment.status } returns status
        return payment
    }

    Given("본인 락이 아닌 좌석 구매 요청") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } throws SeatNotLockOwnerException(1L, 10L)

        When("useCase.execute를 호출하면") {
            Then("SeatNotLockOwnerException이 던져지고 TicketOrder가 생성되지 않는다") {
                shouldThrow<SeatNotLockOwnerException> {
                    useCase.execute(buildCommand())
                }
                verify(exactly = 0) { ticketingDomainService.createPendingOrder(any(), any()) }
            }
        }
    }

    Given("락 검증 성공 + PG가 READY(PENDING) 상태 Payment를 반환하는 정상 결제 준비") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrderResponse = buildPendingOrderResponse()
        val readyPayment = buildPayment(PaymentStatus.READY)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("80000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrderResponse
        every {
            paymentDomainService.create(
                userId = userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = pendingOrderResponse.ticketOrderId,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("80000"),
                currency = "KRW",
            )
        } returns readyPayment

        When("execute 실행 시") {
            val result = useCase.execute(buildCommand())

            Then("PENDING 상태의 TicketOrderResponse가 반환된다") {
                result.status shouldBe OrderStatus.PENDING
                result.ticketOrderId shouldBe 100L
            }

            Then("confirmOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
            }

            Then("cancelOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("락 검증 성공 + PG COMPLETED Payment 반환 (결제 즉시 완료 케이스)") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrderResponse = buildPendingOrderResponse()
        val completedPayment = buildPayment(PaymentStatus.COMPLETED)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("50000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrderResponse
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns completedPayment

        When("execute 실행 시") {
            val result = useCase.execute(buildCommand())

            Then("동기 confirmOrder 없이 PENDING 상태 그대로 반환된다") {
                result.status shouldBe OrderStatus.PENDING
            }

            Then("confirmOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
            }

            Then("cancelOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("락 검증 성공 + PG FAILED Payment 반환") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val pendingOrderResponse = buildPendingOrderResponse()
        val failedPayment = buildPayment(PaymentStatus.FAILED)

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("50000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns pendingOrderResponse
        every {
            paymentDomainService.create(any(), any(), any(), any(), any(), any(), any())
        } returns failedPayment

        When("execute 실행 시") {
            val result = useCase.execute(buildCommand())

            Then("동기 cancelOrder 없이 PENDING 상태 그대로 반환된다") {
                result.status shouldBe OrderStatus.PENDING
            }

            Then("cancelOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }
        }
    }

    Given("빈 lockId로 PurchaseTicketsCommand 생성") {
        When("lockId가 blank인 경우") {
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
