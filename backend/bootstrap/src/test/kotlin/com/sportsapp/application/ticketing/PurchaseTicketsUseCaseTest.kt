package com.sportsapp.application.ticketing
import com.sportsapp.application.ticketing.dto.PurchaseTicketsCommand
import com.sportsapp.application.ticketing.usecase.PurchaseTicketsUseCase

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import com.sportsapp.domain.ticketing.exception.SeatNotLockOwnerException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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

    fun buildOrderResult(orderId: Long = 100L): TicketOrderResult =
        TicketOrderResult(ticketOrderId = orderId, status = OrderStatus.PENDING)

    fun buildPgInitiateResult(paymentId: Long = 999L): PgInitiateResult =
        PgInitiateResult(
            paymentId = paymentId,
            status = PaymentStatus.READY,
            pgTransactionId = "tid-001",
            checkoutUrl = "https://pg.example.com/checkout",
        )

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

    Given("락 검증 성공 — 정상 결제 준비 흐름") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val orderResult = buildOrderResult()
        val pgResult = buildPgInitiateResult()

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("80000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns orderResult
        every {
            paymentDomainService.createPending(
                userId = userId,
                idempotencyKey = idempotencyKey,
                orderType = OrderType.TICKETING,
                orderId = orderResult.ticketOrderId,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("80000"),
                currency = "KRW",
            )
        } returns 999L
        val pgCommandSlot = slot<PgInitiateCommand>()
        every { paymentDomainService.initiatePg(capture(pgCommandSlot)) } returns pgResult

        When("execute 실행 시") {
            val result = useCase.execute(buildCommand())

            Then("PENDING 상태의 TicketOrderResponse가 반환된다") {
                result.status shouldBe OrderStatus.PENDING
                result.ticketOrderId shouldBe 100L
            }

            Then("PG 주문명은 기술 식별자가 아닌 도메인 라벨(티켓 예매)이다") {
                pgCommandSlot.captured.itemName shouldBe OrderType.TICKETING.displayName
            }

            Then("confirmOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.confirmOrder(any(), any()) }
            }

            Then("cancelOrder가 호출되지 않는다") {
                verify(exactly = 0) { ticketingDomainService.cancelOrder(any()) }
            }

            Then("PG 호출(initiatePg)이 트랜잭션 없이 실행된다") {
                verify(exactly = 1) { paymentDomainService.initiatePg(any<PgInitiateCommand>()) }
            }
        }
    }

    Given("락 검증 성공 + createPending 멱등 키 중복 — 동일 paymentId 반환") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = PurchaseTicketsUseCase(ticketingDomainService, paymentDomainService)
        val orderResult = buildOrderResult()
        val pgResult = buildPgInitiateResult()

        every { ticketingDomainService.verifyLockOwner(lockId, userId) } returns Unit
        every { ticketingDomainService.calculateAmount(lockId) } returns BigDecimal("50000")
        every { ticketingDomainService.createPendingOrder(lockId, userId) } returns orderResult
        every { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) } returns 999L
        every { paymentDomainService.initiatePg(any()) } returns pgResult

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
