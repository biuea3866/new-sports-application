package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.dto.ApplyRecruitmentCommand
import com.sportsapp.application.recruitment.usecase.ApplyRecruitmentUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
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
 * ApplicationResponse.of가 createdAt을 참조하므로, DomainService를 모킹하는 UseCase 테스트에서는
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

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = ApplyRecruitmentUseCase(recruitmentDomainService)

    Given("정원 여유가 있는 유료(참가비>0) 모집 신청") {
        val recruitment = recruitmentWithFee(BigDecimal("10000"))
        val pendingApplication = mockApplication(id = 11L, recruitmentId = 1L, status = ApplicationStatus.PENDING)
        every { recruitmentDomainService.getRecruitment(1L) } returns recruitment
        every { recruitmentDomainService.apply(1L, 100L) } returns 11L
        every { recruitmentDomainService.getApplicationById(11L) } returns pendingApplication

        When("execute를 호출하면") {
            val result = useCase.execute(ApplyRecruitmentCommand(recruitmentId = 1L, applicantUserId = 100L))

            Then("PENDING 상태로 신청이 생성되고 confirmApplication은 호출되지 않는다") {
                result.status shouldBe ApplicationStatus.PENDING
                result.paymentId shouldBe null
                verify(exactly = 0) { recruitmentDomainService.confirmApplication(any(), any()) }
            }
        }
    }

    Given("참가비 0원(무료) 모집 신청") {
        val recruitment = recruitmentWithFee(BigDecimal.ZERO)
        val confirmedApplication = mockApplication(id = 22L, recruitmentId = 2L, status = ApplicationStatus.CONFIRMED)
        every { recruitmentDomainService.getRecruitment(2L) } returns recruitment
        every { recruitmentDomainService.apply(2L, 200L) } returns 22L
        every { recruitmentDomainService.confirmApplication(22L, null) } returns confirmedApplication

        When("execute를 호출하면") {
            val result = useCase.execute(ApplyRecruitmentCommand(recruitmentId = 2L, applicantUserId = 200L))

            Then("PG 없이 즉시 CONFIRMED로 확정된다") {
                result.status shouldBe ApplicationStatus.CONFIRMED
                result.paymentId shouldBe null
                verify(exactly = 1) { recruitmentDomainService.confirmApplication(22L, null) }
            }
        }
    }
})
