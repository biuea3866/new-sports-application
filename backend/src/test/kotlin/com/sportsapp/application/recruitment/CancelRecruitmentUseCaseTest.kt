package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.dto.CancelRecruitmentCommand
import com.sportsapp.application.recruitment.usecase.CancelRecruitmentUseCase
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class CancelRecruitmentUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = CancelRecruitmentUseCase(recruitmentDomainService)

    Given("개설자의 모집 취소 요청") {
        val recruitment = Recruitment.create(
            title = "주말 축구 모임",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = null,
            recruiterUserId = 1L,
        )
        recruitment.cancelByHost(1L)
        every { recruitmentDomainService.cancelRecruitment(recruitmentId = 5L, recruiterUserId = 1L) } returns recruitment

        When("execute를 호출하면") {
            val result = useCase.execute(CancelRecruitmentCommand(recruitmentId = 5L, recruiterUserId = 1L))

            Then("CANCELLED 상태의 RecruitmentResponse를 반환한다") {
                result.status shouldBe RecruitmentStatus.CANCELLED
            }
        }
    }
})
