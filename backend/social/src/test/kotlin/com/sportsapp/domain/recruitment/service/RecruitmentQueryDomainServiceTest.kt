package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.exception.NotRecruiterException
import com.sportsapp.domain.recruitment.exception.UnauthorizedApplicationAccessException
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
import java.time.ZoneOffset
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
        mockk(relaxed = true),
        mockk(relaxed = true),
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

    Given("존재하는 신청 건") {
        val application = Application.create(recruitmentId = 1L, applicantUserId = 100L)
        every { applicationRepository.findById(11L) } returns application

        When("getApplicationById를 호출하면") {
            val result = service.getApplicationById(11L)

            Then("해당 신청을 반환한다") {
                result shouldBe application
            }
        }
    }

    Given("존재하지 않는 신청 조회") {
        every { applicationRepository.findById(404L) } returns null

        When("getApplicationById를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getApplicationById(404L)
                }
            }
        }
    }

    Given("본인 소유의 신청과 그 신청이 속한 모집이 존재하는 상황") {
        // Application.createdAt은 JPA @CreatedDate(lateinit) — 실제 영속화 전에는 접근 시 예외가 나므로
        // ApplicationDetail이 참조하는 필드를 relaxed mockk로 스텁한다 (ListMyApplicationsUseCaseTest와 동일 패턴).
        val application = mockk<Application>(relaxed = true)
        every { application.id } returns 11L
        every { application.recruitmentId } returns 1L
        every { application.applicantUserId } returns 100L
        every { application.status } returns ApplicationStatus.CONFIRMED
        every { application.paymentId } returns 701L
        every { application.createdAt } returns ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
        every { applicationRepository.findById(11L) } returns application

        val recruitment = openRecruitment(recruiterUserId = 5L)
        every { recruitmentRepository.findById(1L) } returns recruitment

        When("getApplicationDetailBy(applicationId=11, requesterUserId=100)를 호출하면") {
            val result = service.getApplicationDetailBy(applicationId = 11L, requesterUserId = 100L)

            Then("모집명·참가비를 조인한 상세를 반환한다") {
                result.applicationId shouldBe 11L
                result.recruitmentId shouldBe recruitment.id
                result.recruitmentTitle shouldBe recruitment.title
                result.status shouldBe ApplicationStatus.CONFIRMED
                result.feeAmount shouldBe recruitment.feeAmount
                result.paymentId shouldBe 701L
                result.createdAt shouldBe ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
            }
        }
    }

    Given("본인 소유가 아닌 신청 상세 조회") {
        val application = Application.create(recruitmentId = 1L, applicantUserId = 100L)
        every { applicationRepository.findById(11L) } returns application

        When("getApplicationDetailBy(applicationId=11, requesterUserId=999)를 호출하면") {
            Then("UnauthorizedApplicationAccessException을 던진다") {
                shouldThrow<UnauthorizedApplicationAccessException> {
                    service.getApplicationDetailBy(applicationId = 11L, requesterUserId = 999L)
                }
            }
        }
    }

    Given("존재하지 않는 신청 상세 조회") {
        every { applicationRepository.findById(404L) } returns null

        When("getApplicationDetailBy를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getApplicationDetailBy(applicationId = 404L, requesterUserId = 100L)
                }
            }
        }
    }

    Given("신청은 있으나 참조 모집이 존재하지 않는 상황") {
        val application = Application.create(recruitmentId = 404L, applicantUserId = 100L)
        every { applicationRepository.findById(11L) } returns application
        every { recruitmentRepository.findById(404L) } returns null

        When("getApplicationDetailBy(applicationId=11, requesterUserId=100)를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getApplicationDetailBy(applicationId = 11L, requesterUserId = 100L)
                }
            }
        }
    }
})
