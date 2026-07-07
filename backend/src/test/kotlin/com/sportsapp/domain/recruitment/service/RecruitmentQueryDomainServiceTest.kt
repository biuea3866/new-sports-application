package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.ZonedDateTime

private fun openRecruitment(recruiterUserId: Long = 1L): Recruitment = Recruitment.create(
    title = "주말 축구 모임",
    capacity = 10,
    feeAmount = BigDecimal("10000"),
    activityAt = ZonedDateTime.now().plusDays(10),
    applicationDeadline = ZonedDateTime.now().plusDays(5),
    communityId = null,
    recruiterUserId = recruiterUserId,
)

class RecruitmentQueryDomainServiceTest : BehaviorSpec({

    val recruitmentRepository = mockk<RecruitmentRepository>()
    val applicationRepository = mockk<ApplicationRepository>()
    val distributedLock = mockk<DistributedLock>(relaxed = true)
    val cancellationPolicy = mockk<CancellationPolicy>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)

    val service = RecruitmentDomainService(
        recruitmentRepository,
        applicationRepository,
        distributedLock,
        cancellationPolicy,
        domainEventPublisher,
    )

    Given("특정 신청자 명의의 신청이 2건, 타인 신청이 1건 있는 상황") {
        val ownApplication1 = Application.create(recruitmentId = 1L, applicantUserId = 100L)
        val ownApplication2 = Application.create(recruitmentId = 2L, applicantUserId = 100L)
        every { applicationRepository.findByApplicantUserId(100L) } returns listOf(ownApplication1, ownApplication2)

        When("findApplicationsBy(applicantUserId=100)를 호출하면") {
            val result = service.findApplicationsBy(applicantUserId = 100L)

            Then("본인 신청 2건만 반환되고 타인 신청은 포함되지 않는다") {
                result.size shouldBe 2
                result.all { it.applicantUserId == 100L } shouldBe true
            }
        }
    }

    Given("신청 이력이 없는 사용자") {
        every { applicationRepository.findByApplicantUserId(999L) } returns emptyList()

        When("findApplicationsBy(applicantUserId=999)를 호출하면") {
            val result = service.findApplicationsBy(applicantUserId = 999L)

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }

    Given("개설자가 recruiterUserId=1L인 모집") {
        val recruitment = openRecruitment(recruiterUserId = 1L)
        val application = Application.create(recruitmentId = recruitment.id, applicantUserId = 100L)
        every { recruitmentRepository.findById(recruitment.id) } returns recruitment
        every { applicationRepository.findByRecruitmentId(recruitment.id) } returns listOf(application)

        When("개설자 본인이 findApplications를 호출하면") {
            val result = service.findApplications(recruitmentId = recruitment.id, requesterUserId = 1L)

            Then("신청 목록을 반환한다") {
                result.size shouldBe 1
            }
        }

        When("개설자가 아닌 사용자가 findApplications를 호출하면") {
            Then("NotRecruiterException을 던진다") {
                shouldThrow<NotRecruiterException> {
                    service.findApplications(recruitmentId = recruitment.id, requesterUserId = 999L)
                }
            }
        }
    }

    Given("존재하지 않는 모집 조회") {
        every { recruitmentRepository.findById(404L) } returns null

        When("getRecruitment을 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getRecruitment(404L)
                }
            }
        }
    }
})
