package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListApplicationsUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * 개설자용 신청자 목록이 신청자 표시 이름을 함께 반환한다. 표시 이름은 user 컨텍스트가 소유하므로
 * recruitment 도메인이 아니라 application 레이어(UseCase)가 UserDomainService 로 조회해 조합한다.
 */
class ListApplicationsUseCaseDisplayNameTest : BehaviorSpec({

    val recruitmentDomainService = mockk<RecruitmentDomainService>()
    val userDomainService = mockk<UserDomainService>()
    val listApplicationsUseCase = ListApplicationsUseCase(recruitmentDomainService, userDomainService)

    fun application(id: Long, applicantUserId: Long): Application {
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns id
        every { application.recruitmentId } returns 1L
        every { application.applicantUserId } returns applicantUserId
        every { application.status } returns ApplicationStatus.PENDING
        every { application.paymentId } returns null
        every { application.createdAt } returns ZonedDateTime.now()
        return application
    }

    Given("서로 다른 신청자 2명이 신청한 모집") {
        val applications = listOf(application(100L, 71L), application(101L, 68L))
        every {
            recruitmentDomainService.findApplications(recruitmentId = 1L, requesterUserId = 1L)
        } returns applications
        every { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) } returns
            UserDisplayNames.from(
                listOf(
                    mockk<User>().also {
                        every { it.id } returns 71L
                        every { it.displayName } returns "김철수"
                    },
                    mockk<User>().also {
                        every { it.id } returns 68L
                        every { it.displayName } returns "박영희"
                    },
                ),
            )

        When("execute 를 호출하면") {
            val applicants = listApplicationsUseCase.execute(recruitmentId = 1L, requesterUserId = 1L)

            Then("신청 목록을 반환한다") {
                applicants.size shouldBe 2
                applicants.map { it.id } shouldBe listOf(100L, 101L)
                applicants.first().recruitmentId shouldBe 1L
            }

            Then("신청자 id 와 표시 이름이 함께 반환된다") {
                applicants.map { it.applicantUserId } shouldBe listOf(71L, 68L)
                applicants.map { it.applicantDisplayName } shouldBe listOf("김철수", "박영희")
            }

            Then("표시 이름 조회는 신청자 수와 무관하게 1회다 (N+1 없음)") {
                verify(exactly = 1) { userDomainService.findDisplayNamesBy(listOf(71L, 68L)) }
            }
        }
    }

    Given("신청자가 없는 모집") {
        every {
            recruitmentDomainService.findApplications(recruitmentId = 2L, requesterUserId = 1L)
        } returns emptyList()
        every { userDomainService.findDisplayNamesBy(emptyList()) } returns UserDisplayNames.from(emptyList())

        When("execute 를 호출하면") {
            val applicants = listApplicationsUseCase.execute(recruitmentId = 2L, requesterUserId = 1L)

            Then("빈 목록을 반환한다") {
                applicants.isEmpty() shouldBe true
            }
        }
    }

    Given("닉네임을 설정하지 않은 신청자") {
        every {
            recruitmentDomainService.findApplications(recruitmentId = 3L, requesterUserId = 1L)
        } returns listOf(application(102L, 99L))
        every { userDomainService.findDisplayNamesBy(listOf(99L)) } returns UserDisplayNames.from(emptyList())

        When("execute 를 호출하면") {
            val applicants = listApplicationsUseCase.execute(recruitmentId = 3L, requesterUserId = 1L)

            Then("이메일·내부 식별자 대신 기본 표시 이름을 반환한다") {
                applicants.single().applicantDisplayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }
})
