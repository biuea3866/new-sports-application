package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.dto.CreateRecruitmentCommand
import com.sportsapp.application.recruitment.usecase.CreateRecruitmentUseCase
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

class CreateRecruitmentUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = CreateRecruitmentUseCase(recruitmentDomainService)

    Given("정상적인 모집 생성 요청") {
        val activityAt = ZonedDateTime.now().plusDays(10)
        val deadline = ZonedDateTime.now().plusDays(5)
        val command = CreateRecruitmentCommand(
            title = "주말 축구 모임",
            description = "설명",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = activityAt,
            applicationDeadline = deadline,
            communityId = null,
            recruiterUserId = 1L,
        )
        val recruitment = Recruitment.create(
            title = command.title,
            description = command.description,
            capacity = command.capacity,
            feeAmount = command.feeAmount,
            activityAt = command.activityAt,
            applicationDeadline = command.applicationDeadline,
            communityId = command.communityId,
            recruiterUserId = command.recruiterUserId,
        )
        every {
            recruitmentDomainService.create(
                title = command.title,
                description = command.description,
                capacity = command.capacity,
                feeAmount = command.feeAmount,
                activityAt = command.activityAt,
                applicationDeadline = command.applicationDeadline,
                communityId = command.communityId,
                recruiterUserId = command.recruiterUserId,
            )
        } returns recruitment

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("생성된 모집 정보를 담은 RecruitmentResponse를 반환한다") {
                result.title shouldBe "주말 축구 모임"
                result.status shouldBe RecruitmentStatus.OPEN
                result.recruiterUserId shouldBe 1L
            }
        }
    }
})
