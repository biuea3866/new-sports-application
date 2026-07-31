package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.repository.PaymentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * booking(W1-11c) 등 만료 스위퍼의 만료 금지 가드가 소비하는
 * [PaymentDomainService.findUnexpirableOrderIds]를 검증한다.
 *
 * PaymentStatus 전이는 PENDING → READY → COMPLETED(→ REFUNDED)이며, 결제 개시 시점에
 * 이미 PENDING 행이 생성된다. 사용자가 PG 결제창에 있는 동안(READY)도 만료 금지 대상이어야
 * "결제 성공 건만 본다"는 오판(REQUEST_CHANGES ①)이 재발하지 않는다.
 */
class PaymentDomainServiceFindUnexpirableOrderIdsTest : BehaviorSpec({

    fun buildService(paymentRepository: PaymentRepository) = PaymentDomainService(
        paymentRepository = paymentRepository,
        paymentGateway = mockk<PaymentGateway>(),
        domainEventPublisher = mockk(relaxed = true),
        transactionTemplate = mockk(relaxed = true),
    )

    Given("주문 id 목록 중 만료 금지 대상(결제 완료 등) 건이 섞여 있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)
        every { paymentRepository.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L)) } returns setOf(2L)

        When("findUnexpirableOrderIds를 호출하면") {
            val result = service.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L))

            Then("만료 금지 대상 orderId만 반환한다") {
                result shouldBe setOf(2L)
            }
        }
    }

    Given("주문 id 목록이 비어있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)

        When("findUnexpirableOrderIds를 호출하면") {
            val result = service.findUnexpirableOrderIds(OrderType.BOOKING, emptyList())

            Then("repository 호출 없이 빈 집합을 반환한다") {
                result shouldBe emptySet()
                verify(exactly = 0) { paymentRepository.findUnexpirableOrderIds(any(), any()) }
            }
        }
    }
})
