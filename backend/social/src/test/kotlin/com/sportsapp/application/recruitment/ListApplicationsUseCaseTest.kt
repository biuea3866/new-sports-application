package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListApplicationsUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListApplicationsUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = ListApplicationsUseCase(recruitmentDomainService)

    Given("개설자가 자신의 모집 신청 목록을 조회하는 상황") {
        // Application.createdAt은 JPA @CreatedDate(lateinit) — 실제 영속화 전에는 접근 시 예외가 나므로
        // ApplicationResponse.of가 참조하는 필드를 relaxed mockk로 스텁한다.
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 100L
        every { application.recruitmentId } returns 1L
        every { application.status } returns ApplicationStatus.PENDING
        every { application.paymentId } returns null
        every { application.createdAt } returns ZonedDateTime.now()
        every { recruitmentDomainService.findApplications(recruitmentId = 1L, requesterUserId = 1L) } returns listOf(application)

        When("execute(recruitmentId=1, requesterUserId=1)를 호출하면") {
            val result = useCase.execute(1L, 1L)

            Then("신청 목록을 반환한다") {
                result.size shouldBe 1
                result[0].recruitmentId shouldBe 1L
            }
        }
    }
})
