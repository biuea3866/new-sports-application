package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.ListRecruitmentsUseCase
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZonedDateTime

class ListRecruitmentsUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<RecruitmentDomainService, CommunityDomainService, ListRecruitmentsUseCase> {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(recruitmentDomainService, communityDomainService, ListRecruitmentsUseCase(recruitmentDomainService, communityDomainService))
    }

    fun publicCommunity() = Community.create(
        name = "모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = 1L,
    )

    Given("communityId 없이 전역 모집 목록을 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = Recruitment.create(
            title = "주말 축구 모임",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = null,
            recruiterUserId = 1L,
        )
        every { recruitmentDomainService.listRecruitments(null) } returns listOf(recruitment)

        When("execute(communityId=null)를 호출하면") {
            val result = useCase.execute(null)

            Then("전역 모집 목록을 반환하고 community 인가는 호출되지 않는다") {
                result.size shouldBe 1
                verify(exactly = 0) { communityDomainService.getCommunity(any(), any()) }
            }
        }
    }

    Given("PUBLIC 커뮤니티에 소속된 모집 목록을 비멤버가 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = Recruitment.create(
            title = "주말 축구 모임",
            capacity = 10,
            feeAmount = BigDecimal("10000"),
            activityAt = ZonedDateTime.now().plusDays(10),
            applicationDeadline = ZonedDateTime.now().plusDays(5),
            communityId = 7L,
            recruiterUserId = 1L,
        )
        every { communityDomainService.getCommunity(7L, 2L) } returns publicCommunity()
        every { recruitmentDomainService.listRecruitments(7L) } returns listOf(recruitment)

        When("execute(communityId=7, requesterId=2)를 호출하면") {
            val result = useCase.execute(communityId = 7L, requesterId = 2L)

            Then("해당 커뮤니티 모집 목록을 반환한다") {
                result.size shouldBe 1
                result[0].communityId shouldBe 7L
            }
        }
    }

    Given("PRIVATE 커뮤니티에 소속된 모집 목록을 비멤버가 조회하면") {
        val (_, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.getCommunity(20L, 3L) } throws NotCommunityMemberException(20L, 3L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(communityId = 20L, requesterId = 3L)
            }
        }
    }

    Given("PRIVATE 커뮤니티에 소속된 모집 목록을 requesterId 없이(비로그인) 조회하면") {
        val (_, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.getCommunity(30L, 0L) } throws NotCommunityMemberException(30L, 0L)

        Then("게스트 sentinel 로 조회해 NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(communityId = 30L, requesterId = null)
            }
        }
    }
})
