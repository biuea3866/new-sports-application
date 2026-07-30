package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.application.recruitment.usecase.CancelApplicationUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class CancelApplicationUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = CancelApplicationUseCase(recruitmentDomainService)

    Given("CONFIRMED 상태의 본인 신청 취소 요청") {
        // Application.createdAt은 JPA @CreatedDate(lateinit) — 실제 영속화 전에는 접근 시 예외가 나므로
        // ApplicationResponse.of가 참조하는 필드를 relaxed mockk로 스텁한다.
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 10L
        every { application.recruitmentId } returns 1L
        every { application.status } returns ApplicationStatus.CANCELLED
        every { application.paymentId } returns 100L
        every { application.createdAt } returns ZonedDateTime.now()
        every { recruitmentDomainService.cancelApplication(applicationId = 10L, applicantUserId = 100L) } returns application

        When("execute를 호출하면") {
            val result = useCase.execute(CancelApplicationCommand(applicationId = 10L, applicantUserId = 100L))

            Then("CANCELLED 상태의 ApplicationResponse를 반환한다") {
                result.status shouldBe ApplicationStatus.CANCELLED
            }
        }
    }
})
