package com.sportsapp.presentation.recruitment.worker

import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.gateway.RecruitmentRefundGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class RecruitmentRefundEventWorkerTest : BehaviorSpec({

    Given("환불 게이트가 정상 동작하는 상황") {
        val gateway = mockk<RecruitmentRefundGateway>(relaxed = true)
        val worker = RecruitmentRefundEventWorker(gateway)
        val event = ApplicationRefundRequestedEvent(
            applicationId = 1L,
            paymentId = 100L,
            refundAmount = BigDecimal("9500.00"),
            reason = "APPLICANT_CANCEL",
        )

        When("handleRefundRequested를 호출하면") {
            worker.handleRefundRequested(event)

            Then("게이트에 환불이 요청된다") {
                verify(exactly = 1) { gateway.requestRefund(100L, BigDecimal("9500.00"), "APPLICANT_CANCEL") }
            }
        }
    }

    Given("환불 게이트가 예외를 던지는 상황") {
        val gateway = mockk<RecruitmentRefundGateway>()
        every { gateway.requestRefund(any(), any(), any()) } throws RuntimeException("PG 통신 장애")
        val worker = RecruitmentRefundEventWorker(gateway)
        val event = ApplicationRefundRequestedEvent(
            applicationId = 2L,
            paymentId = 200L,
            refundAmount = BigDecimal("10000.00"),
            reason = "RECRUITMENT_CANCELLED",
        )

        When("handleRefundRequested를 호출하면") {
            Then("예외가 전파되지 않고 에러 로그만 남긴다(원 취소 트랜잭션은 이미 커밋된 상태라 영향받지 않는다)") {
                worker.handleRefundRequested(event)
            }
        }
    }

    Given("무료(참가비 0원) 신청 취소로 paymentId가 없는 상황") {
        val gateway = mockk<RecruitmentRefundGateway>(relaxed = true)
        val worker = RecruitmentRefundEventWorker(gateway)
        val event = ApplicationRefundRequestedEvent(
            applicationId = 3L,
            paymentId = null,
            refundAmount = BigDecimal.ZERO,
            reason = "APPLICANT_CANCEL",
        )

        When("handleRefundRequested를 호출하면") {
            worker.handleRefundRequested(event)

            Then("환불 대상 결제가 없으므로 게이트를 호출하지 않는다") {
                verify(exactly = 0) { gateway.requestRefund(any(), any(), any()) }
            }
        }
    }
})
