package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.repository.PaymentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime

/**
 * booking(W1-11c) 등 만료 스위퍼의 만료 금지 가드가 소비하는
 * [PaymentDomainService.findUnexpirableOrderIds]를 검증한다.
 *
 * 재확정(재리뷰): 이전에는 status만으로("결제 완료만 확인") 판단해, 결제 개시 시점에 이미
 * 생성되는 PENDING/READY 행이 모든 주문에 항상 존재하는 실제 데이터 형태를 재현하지 못했고,
 * 그 결과 스위퍼가 전건 만료 금지로 무력화됐다(재발 방지 근거). status+updatedAt 판정 규칙
 * 자체는 [PaymentExpiryGuardTest]에서 실제 값 조합으로 exhaustive 검증하고, 이 테스트는
 * PaymentDomainService가 activeWindowMinutes로부터 activeSince(now 내부 계산,
 * no-time-parameter)를 계산해 repository에 위임하는 배선만 검증한다.
 */
class PaymentDomainServiceFindUnexpirableOrderIdsTest : BehaviorSpec({

    fun buildService(paymentRepository: PaymentRepository) = PaymentDomainService(
        paymentRepository = paymentRepository,
        paymentGateway = mockk<PaymentGateway>(),
        domainEventPublisher = mockk(relaxed = true),
        transactionTemplate = mockk(relaxed = true),
    )

    Given("주문 id 목록 중 만료 금지 대상(결제 진행 중·완료)이 섞여 있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)
        val activeSinceSlot = slot<ZonedDateTime>()
        every {
            paymentRepository.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L), capture(activeSinceSlot))
        } returns setOf(2L)

        When("findUnexpirableOrderIds(activeWindowMinutes=5)를 호출하면") {
            val result = service.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L), activeWindowMinutes = 5)

            Then("repository가 판정한 만료 금지 대상 orderId를 그대로 반환한다") {
                result shouldBe setOf(2L)
            }

            Then("activeSince는 이 메서드 내부에서 now - activeWindowMinutes로 계산된다 (no-time-parameter)") {
                val diff = Duration.between(activeSinceSlot.captured, ZonedDateTime.now().minusMinutes(5)).abs().seconds
                (diff < 5) shouldBe true
            }
        }
    }

    Given("주문 id 목록이 비어있을 때") {
        val paymentRepository = mockk<PaymentRepository>()
        val service = buildService(paymentRepository)

        When("findUnexpirableOrderIds를 호출하면") {
            val result = service.findUnexpirableOrderIds(OrderType.BOOKING, emptyList(), activeWindowMinutes = 5)

            Then("repository 호출 없이 빈 집합을 반환한다") {
                result shouldBe emptySet()
                verify(exactly = 0) { paymentRepository.findUnexpirableOrderIds(any(), any(), any()) }
            }
        }
    }
})
