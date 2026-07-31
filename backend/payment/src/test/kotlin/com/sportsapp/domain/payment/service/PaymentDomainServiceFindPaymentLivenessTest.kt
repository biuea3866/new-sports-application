package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.repository.PaymentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * booking(W1-11c) 등 만료 스위퍼가 소비하는 [PaymentDomainService.findPaymentLiveness]를
 * 검증한다.
 *
 * 4차 재설계: 이전(3차) `findUnexpirableOrderIds`는 activeWindowMinutes(시간 창)를 인자로
 * 받아 payment가 시간 정책을 가졌으나, 이 설계는 payment가 **사실만** 답하도록 시간 인자를
 * 제거했다 — TTL 정책은 호출 컨텍스트(booking 등)가 소유한다. 이 메서드는 orderIds가
 * 주어지면 repository 조회 결과를 그대로 반환하는 배선만 검증한다. live/settled 판정 규칙
 * 자체는 [PaymentLivenessClassifierTest]에서 exhaustive 검증한다.
 */
class PaymentDomainServiceFindPaymentLivenessTest : BehaviorSpec({

    fun buildService(paymentRepository: PaymentRepository) = PaymentDomainService(
        paymentRepository = paymentRepository,
        paymentGateway = mockk<PaymentGateway>(),
        domainEventPublisher = mockk(relaxed = true),
        transactionTemplate = mockk(relaxed = true),
    )

    Given("주문 id 목록 중 live·settled 대상이 섞여 있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)
        val expected = PaymentLivenessQueryResult(liveOrderIds = setOf(2L, 3L), settledOrderIds = setOf(3L))
        every {
            paymentRepository.findPaymentLiveness(OrderType.BOOKING, listOf(1L, 2L, 3L))
        } returns expected

        When("findPaymentLiveness를 호출하면") {
            val result = service.findPaymentLiveness(OrderType.BOOKING, listOf(1L, 2L, 3L))

            Then("repository가 판정한 결과를 그대로 반환한다 (시간 인자 없이)") {
                result shouldBe expected
            }
        }
    }

    Given("주문 id 목록이 비어있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)

        When("findPaymentLiveness를 호출하면") {
            val result = service.findPaymentLiveness(OrderType.BOOKING, emptyList())

            Then("repository 호출 없이 빈 결과를 반환한다") {
                result shouldBe PaymentLivenessQueryResult.empty()
                verify(exactly = 0) { paymentRepository.findPaymentLiveness(any(), any()) }
            }
        }
    }
})
