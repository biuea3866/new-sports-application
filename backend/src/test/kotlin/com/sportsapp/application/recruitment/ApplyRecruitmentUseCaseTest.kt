package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.dto.ApplyRecruitmentCommand
import com.sportsapp.application.recruitment.usecase.ApplyRecruitmentUseCase
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

private fun recruitmentWithFee(feeAmount: BigDecimal): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = 10,
    feeAmount = feeAmount,
    activityAt = ZonedDateTime.now().plusDays(10),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = null,
    recruiterUserId = 1L,
)

/**
 * Application.createdAt은 JPA `@CreatedDate`(lateinit) — 실제 영속화 전에는 접근 시 예외가 난다.
 * ApplyRecruitmentResult.of가 createdAt을 참조하므로, DomainService를 모킹하는 UseCase 테스트에서는
 * relaxed mockk로 필요한 필드만 스텁한다 (booking GetBookingUseCaseTest와 동일 패턴).
 */
private fun mockApplication(
    id: Long,
    recruitmentId: Long,
    status: ApplicationStatus,
    paymentId: Long? = null,
): Application {
    val application = mockk<Application>(relaxed = true)
    every { application.id } returns id
    every { application.recruitmentId } returns recruitmentId
    every { application.status } returns status
    every { application.paymentId } returns paymentId
    every { application.createdAt } returns ZonedDateTime.now()
    return application
}

class ApplyRecruitmentUseCaseTest : BehaviorSpec({

    Given("정원 여유가 있는 유료(참가비>0) 모집 신청") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = ApplyRecruitmentUseCase(recruitmentDomainService, paymentDomainService)

        val recruitment = recruitmentWithFee(BigDecimal("10000"))
        val pendingApplication = mockApplication(id = 11L, recruitmentId = 1L, status = ApplicationStatus.PENDING)
        val pgResult = PgInitiateResult(
            paymentId = 99L,
            status = PaymentStatus.READY,
            pgTransactionId = "tid-recruitment-001",
            checkoutUrl = "http://checkout/recruitment",
        )
        every { recruitmentDomainService.getRecruitment(1L) } returns recruitment
        every { recruitmentDomainService.apply(1L, 100L) } returns 11L
        every { recruitmentDomainService.getApplicationById(11L) } returns pendingApplication
        every {
            paymentDomainService.createPending(
                userId = 100L,
                idempotencyKey = any(),
                orderType = OrderType.RECRUITMENT,
                orderId = 11L,
                method = PaymentMethod.CREDIT_CARD,
                amount = BigDecimal("10000"),
                currency = "KRW",
            )
        } returns 99L
        val pgCommandSlot = slot<PgInitiateCommand>()
        every { paymentDomainService.initiatePg(capture(pgCommandSlot)) } returns pgResult

        When("execute를 호출하면") {
            val command = ApplyRecruitmentCommand(
                recruitmentId = 1L,
                applicantUserId = 100L,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                currency = "KRW",
            )
            val result = useCase.execute(command)

            Then("PENDING 상태로 신청이 생성되고 PG 개시로 paymentId·checkoutUrl이 채워진다") {
                result.status shouldBe ApplicationStatus.PENDING
                result.paymentId shouldBe 99L
                result.checkoutUrl shouldBe "http://checkout/recruitment"
            }

            Then("PG 주문명은 기술 식별자가 아닌 모집 제목이다") {
                pgCommandSlot.captured.itemName shouldBe recruitment.title
            }

            Then("결제 개시(createPending+initiatePg)가 호출되고 confirmApplication은 호출되지 않는다") {
                verify(exactly = 1) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 1) { paymentDomainService.initiatePg(any()) }
                verify(exactly = 0) { recruitmentDomainService.confirmApplication(any(), any()) }
            }
        }
    }

    Given("참가비 0원(무료) 모집 신청") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val useCase = ApplyRecruitmentUseCase(recruitmentDomainService, paymentDomainService)

        val recruitment = recruitmentWithFee(BigDecimal.ZERO)
        val confirmedApplication = mockApplication(id = 22L, recruitmentId = 2L, status = ApplicationStatus.CONFIRMED)
        every { recruitmentDomainService.getRecruitment(2L) } returns recruitment
        every { recruitmentDomainService.apply(2L, 200L) } returns 22L
        every { recruitmentDomainService.confirmApplication(22L, null) } returns confirmedApplication

        When("execute를 호출하면") {
            val command = ApplyRecruitmentCommand(
                recruitmentId = 2L,
                applicantUserId = 200L,
                paymentMethod = PaymentMethod.CREDIT_CARD,
                currency = "KRW",
            )
            val result = useCase.execute(command)

            Then("PG 없이 즉시 CONFIRMED로 확정되고 checkoutUrl은 없다") {
                result.status shouldBe ApplicationStatus.CONFIRMED
                result.paymentId shouldBe null
                result.checkoutUrl shouldBe null
                verify(exactly = 1) { recruitmentDomainService.confirmApplication(22L, null) }
            }

            Then("결제 개시(createPending/initiatePg)는 호출되지 않는다") {
                verify(exactly = 0) { paymentDomainService.createPending(any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { paymentDomainService.initiatePg(any()) }
            }
        }
    }
})
