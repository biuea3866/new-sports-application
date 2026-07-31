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
 * booking(W1-11c) 등 만료 스위퍼의 결제 성공 가드가 소비하는
 * [PaymentDomainService.findCompletedOrderIds]를 검증한다.
 */
class PaymentDomainServiceFindCompletedOrderIdsTest : BehaviorSpec({

    fun buildService(paymentRepository: PaymentRepository) = PaymentDomainService(
        paymentRepository = paymentRepository,
        paymentGateway = mockk<PaymentGateway>(),
        domainEventPublisher = mockk(relaxed = true),
        transactionTemplate = mockk(relaxed = true),
    )

    Given("주문 id 목록 중 결제 완료 건이 섞여 있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)
        every { paymentRepository.findCompletedOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L)) } returns setOf(2L)

        When("findCompletedOrderIds를 호출하면") {
            val result = service.findCompletedOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L))

            Then("완료된 주문 id만 반환한다") {
                result shouldBe setOf(2L)
            }
        }
    }

    Given("주문 id 목록이 비어있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)

        When("findCompletedOrderIds를 호출하면") {
            val result = service.findCompletedOrderIds(OrderType.BOOKING, emptyList())

            Then("repository 호출 없이 빈 집합을 반환한다") {
                result shouldBe emptySet()
                verify(exactly = 0) { paymentRepository.findCompletedOrderIds(any(), any()) }
            }
        }
    }
})
