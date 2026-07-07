package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListRecruitmentsUseCase
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class ListRecruitmentsUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = ListRecruitmentsUseCase(recruitmentDomainService)

    Given("특정 커뮤니티에 소속된 모집 목록 조회") {
        val recruitment = Recruitment.create(
            title = "주말 축구 모임",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = 7L,
            recruiterUserId = 1L,
        )
        every { recruitmentDomainService.listRecruitments(7L) } returns listOf(recruitment)

        When("execute(communityId=7)를 호출하면") {
            val result = useCase.execute(7L)

            Then("해당 커뮤니티 모집 목록을 반환한다") {
                result.size shouldBe 1
                result[0].communityId shouldBe 7L
            }
        }
    }
})
