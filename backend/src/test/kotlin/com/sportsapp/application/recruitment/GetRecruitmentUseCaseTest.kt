package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.GetRecruitmentUseCase
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class GetRecruitmentUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = GetRecruitmentUseCase(recruitmentDomainService)

    Given("존재하는 모집 조회") {
        val recruitment = Recruitment.create(
            title = "주말 축구 모임",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = null,
            recruiterUserId = 1L,
        )
        every { recruitmentDomainService.getRecruitment(1L) } returns recruitment

        When("execute를 호출하면") {
            val result = useCase.execute(1L)

            Then("모집 정보를 담은 RecruitmentResponse를 반환한다") {
                result.title shouldBe "주말 축구 모임"
            }
        }
    }
})
