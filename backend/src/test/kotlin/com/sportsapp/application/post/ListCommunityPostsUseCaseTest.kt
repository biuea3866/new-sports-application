package com.sportsapp.application.post

import com.sportsapp.application.post.usecase.ListCommunityPostsUseCase
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import org.springframework.data.domain.PageImpl

class ListCommunityPostsUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<PostDomainService, CommunityDomainService, ListCommunityPostsUseCase> {
        val postDomainService = mockk<PostDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(postDomainService, communityDomainService, ListCommunityPostsUseCase(postDomainService, communityDomainService))
    }

    fun publicCommunity() = Community.create(
        name = "모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = 1L,
    )

    Given("PUBLIC 모임의 게시글 목록을 비멤버가 조회하면") {
        val (postDomainService, communityDomainService, useCase) = newUseCase()
        val criteriaSlot = slot<PostSearchCriteria>()
        every { communityDomainService.getCommunity(10L, 2L) } returns publicCommunity()
        every { postDomainService.search(capture(criteriaSlot), any()) } returns PageImpl(emptyList<Post>())

        When("execute를 호출하면") {
            val result = useCase.execute(communityId = 10L, requesterId = 2L, sportCategory = null, page = 0, size = 20)

            Then("communityId 필터로 목록을 조회한다") {
                result.totalElements shouldBe 0
                criteriaSlot.captured.communityId shouldBe 10L
                criteriaSlot.captured.globalFeedOnly shouldBe false
            }
        }
    }

    Given("PRIVATE 모임의 게시글 목록을 비멤버가 조회하면") {
        val (_, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.getCommunity(20L, 3L) } throws NotCommunityMemberException(20L, 3L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(communityId = 20L, requesterId = 3L, sportCategory = null, page = 0, size = 20)
            }
        }
    }

    Given("PRIVATE 모임의 게시글 목록을 requesterId 없이(비로그인) 조회하면") {
        val (_, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.getCommunity(30L, 0L) } throws NotCommunityMemberException(30L, 0L)

        Then("게스트 sentinel 로 조회해 NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(communityId = 30L, requesterId = null, sportCategory = null, page = 0, size = 20)
            }
        }
    }

    Given("0건인 모임 게시글 목록을 조회하면") {
        val (postDomainService, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.getCommunity(40L, 1L) } returns publicCommunity()
        every { postDomainService.search(any(), any()) } returns PageImpl(emptyList<Post>())

        When("execute를 호출하면") {
            val result = useCase.execute(communityId = 40L, requesterId = 1L, sportCategory = SportCategory.RUNNING, page = 0, size = 20)

            Then("빈 목록이 반환된다") {
                result.totalElements shouldBe 0
            }
        }
    }
})
