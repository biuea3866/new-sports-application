package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListMyApplicationsUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListMyApplicationsUseCaseTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val useCase = ListMyApplicationsUseCase(recruitmentDomainService)

    Given("신청 이력이 있는 사용자") {
        // Application.createdAt은 JPA @CreatedDate(lateinit) — 실제 영속화 전에는 접근 시 예외가 나므로
        // ApplicationResponse.of가 참조하는 필드를 relaxed mockk로 스텁한다.
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 1L
        every { application.recruitmentId } returns 1L
        every { application.status } returns ApplicationStatus.PENDING
        every { application.paymentId } returns null
        every { application.createdAt } returns ZonedDateTime.now()
        every { recruitmentDomainService.findApplicationsBy(100L) } returns listOf(application)

        When("execute(applicantUserId=100)를 호출하면") {
            val result = useCase.execute(100L)

            Then("본인 신청 목록을 반환한다") {
                result.size shouldBe 1
                result[0].recruitmentId shouldBe 1L
            }
        }
    }

    Given("신청 이력이 없는 사용자") {
        every { recruitmentDomainService.findApplicationsBy(999L) } returns emptyList()

        When("execute(applicantUserId=999)를 호출하면") {
            val result = useCase.execute(999L)

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
