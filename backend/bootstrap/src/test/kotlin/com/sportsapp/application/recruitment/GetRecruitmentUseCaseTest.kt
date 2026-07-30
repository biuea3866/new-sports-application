package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.GetRecruitmentUseCase
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

class GetRecruitmentUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<RecruitmentDomainService, CommunityDomainService, GetRecruitmentUseCase> {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(recruitmentDomainService, communityDomainService, GetRecruitmentUseCase(recruitmentDomainService, communityDomainService))
    }

    fun recruitmentOf(communityId: Long?) = Recruitment.create(
        title = "주말 축구 모임",
        capacity = 10,
        feeAmount = BigDecimal("10000"),
        activityAt = ZonedDateTime.now().plusDays(10),
        applicationDeadline = ZonedDateTime.now().plusDays(5),
        communityId = communityId,
        recruiterUserId = 1L,
    )

    fun publicCommunity() = Community.create(
        name = "모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = 1L,
    )

    Given("소속 community 가 없는 모집을 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = recruitmentOf(communityId = null)
        every { recruitmentDomainService.getRecruitment(1L) } returns recruitment

        When("execute를 호출하면") {
            val result = useCase.execute(1L)

            Then("모집 정보를 담은 RecruitmentResponse를 반환하고 community 인가는 호출되지 않는다") {
                result.title shouldBe "주말 축구 모임"
                verify(exactly = 0) { communityDomainService.getCommunity(any(), any()) }
            }
        }
    }

    Given("PUBLIC 모임 소속 모집을 비멤버가 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = recruitmentOf(communityId = 10L)
        every { recruitmentDomainService.getRecruitment(2L) } returns recruitment
        every { communityDomainService.getCommunity(10L, 3L) } returns publicCommunity()

        When("execute를 요청자 3L로 호출하면") {
            val result = useCase.execute(recruitmentId = 2L, requesterId = 3L)

            Then("정상 통과한다") {
                result.communityId shouldBe 10L
            }
        }
    }

    Given("PRIVATE 모임 소속 모집을 비멤버가 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = recruitmentOf(communityId = 20L)
        every { recruitmentDomainService.getRecruitment(3L) } returns recruitment
        every { communityDomainService.getCommunity(20L, 4L) } throws NotCommunityMemberException(20L, 4L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(recruitmentId = 3L, requesterId = 4L)
            }
        }
    }

    Given("PRIVATE 모임 소속 모집을 requesterId 없이(비로그인) 조회하면") {
        val (recruitmentDomainService, communityDomainService, useCase) = newUseCase()
        val recruitment = recruitmentOf(communityId = 30L)
        every { recruitmentDomainService.getRecruitment(4L) } returns recruitment
        every { communityDomainService.getCommunity(30L, 0L) } throws NotCommunityMemberException(30L, 0L)

        Then("게스트 sentinel 로 조회해 NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(recruitmentId = 4L, requesterId = null)
            }
        }
    }
})
