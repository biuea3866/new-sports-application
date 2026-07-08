package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationCustomRepository
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

class RecruitmentDomainServiceCatalogReadTest : BehaviorSpec({

    fun buildService(
        recruitmentRepository: RecruitmentRepository = mockk(relaxed = true),
        applicationRepository: ApplicationRepository = mockk(relaxed = true),
        distributedLock: DistributedLock = mockk(relaxed = true),
        cancellationPolicy: CancellationPolicy = mockk(relaxed = true),
        domainEventPublisher: DomainEventPublisher = mockk(relaxed = true),
        recruitmentCustomRepository: RecruitmentCustomRepository = mockk(relaxed = true),
        applicationCustomRepository: ApplicationCustomRepository = mockk(relaxed = true),
    ) = RecruitmentDomainService(
        recruitmentRepository = recruitmentRepository,
        applicationRepository = applicationRepository,
        distributedLock = distributedLock,
        cancellationPolicy = cancellationPolicy,
        domainEventPublisher = domainEventPublisher,
        recruitmentCustomRepository = recruitmentCustomRepository,
        applicationCustomRepository = applicationCustomRepository,
    )

    val activityAt = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    fun recruitmentOf(title: String): Recruitment = Recruitment.create(
        title = title,
        capacity = 10,
        feeAmount = BigDecimal.ZERO,
        activityAt = activityAt,
        applicationDeadline = activityAt.minusDays(1),
        communityId = null,
        recruiterUserId = 1L,
    )

    Given("keyword로 catalog 모집을 검색할 때") {
        val recruitmentCustomRepository = mockk<RecruitmentCustomRepository>()
        val service = buildService(recruitmentCustomRepository = recruitmentCustomRepository)
        val pageable = PageRequest.of(0, 10)
        val recruitment = recruitmentOf("주말 축구 모임")
        val page: Page<Recruitment> = PageImpl(listOf(recruitment), pageable, 1L)

        every { recruitmentCustomRepository.searchOpen("축구", pageable) } returns page

        When("searchOpenRecruitments(keyword=\"축구\", pageable)를 호출하면") {
            val result = service.searchOpenRecruitments(keyword = "축구", pageable = pageable)

            Then("keyword를 그대로 위임한다") {
                verify(exactly = 1) { recruitmentCustomRepository.searchOpen("축구", pageable) }
            }

            Then("조회된 모집 페이지가 그대로 반환된다") {
                result.totalElements shouldBe 1L
                result.content.first().title shouldBe "주말 축구 모임"
            }
        }
    }

    Given("keyword 없이 catalog 모집을 검색할 때") {
        val recruitmentCustomRepository = mockk<RecruitmentCustomRepository>()
        val service = buildService(recruitmentCustomRepository = recruitmentCustomRepository)
        val pageable = PageRequest.of(0, 10)
        val page: Page<Recruitment> = PageImpl(emptyList(), pageable, 0L)

        every { recruitmentCustomRepository.searchOpen(null, pageable) } returns page

        When("searchOpenRecruitments(keyword=null, pageable)를 호출하면") {
            service.searchOpenRecruitments(keyword = null, pageable = pageable)

            Then("keyword=null로 위임한다 (OPEN 전량 대상, 상태 보호는 인프라 레이어가 담당)") {
                verify(exactly = 1) { recruitmentCustomRepository.searchOpen(null, pageable) }
            }
        }
    }

    Given("사용자별 Application(모집명 포함) 목록을 조회할 때") {
        val applicationCustomRepository = mockk<ApplicationCustomRepository>()
        val service = buildService(applicationCustomRepository = applicationCustomRepository)
        val expected = listOf(
            ApplicationWithRecruitmentTitle(applicationId = 1L, status = ApplicationStatus.CONFIRMED, recruitmentTitle = "주말 축구 모임"),
        )
        every { applicationCustomRepository.findBy(9L) } returns expected

        When("listApplicationsWithTitleBy(9L)를 호출하면") {
            val result = service.listApplicationsWithTitleBy(9L)

            Then("모집명이 포함된 신청 목록이 그대로 반환된다") {
                result shouldBe expected
            }
        }
    }

    Given("신청 이력이 없는 사용자") {
        val applicationCustomRepository = mockk<ApplicationCustomRepository>()
        val service = buildService(applicationCustomRepository = applicationCustomRepository)
        every { applicationCustomRepository.findBy(999L) } returns emptyList()

        When("listApplicationsWithTitleBy(999L)를 호출하면") {
            val result = service.listApplicationsWithTitleBy(999L)

            Then("빈 목록을 정상 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
