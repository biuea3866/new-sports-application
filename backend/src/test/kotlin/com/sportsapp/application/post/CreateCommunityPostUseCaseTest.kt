package com.sportsapp.application.post

import com.sportsapp.application.post.dto.CreateCommunityPostCommand
import com.sportsapp.application.post.usecase.CreateCommunityPostUseCase
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.exception.NoticeRequiresHostException
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.PostType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify

class CreateCommunityPostUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<PostDomainService, CommunityDomainService, CreateCommunityPostUseCase> {
        val postDomainService = mockk<PostDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(postDomainService, communityDomainService, CreateCommunityPostUseCase(postDomainService, communityDomainService))
    }

    fun publicCommunity(hostUserId: Long = 1L) = Community.create(
        name = "주말 축구 모임",
        description = null,
        visibility = CommunityVisibility.PUBLIC,
        sportCategory = SportCategory.SOCCER,
        hostUserId = hostUserId,
    )

    fun privateCommunity(hostUserId: Long = 1L) = Community.create(
        name = "비공개 모임",
        description = null,
        visibility = CommunityVisibility.PRIVATE,
        sportCategory = SportCategory.TENNIS,
        hostUserId = hostUserId,
    )

    Given("ACTIVE 멤버(방장)가 모임 게시글을 작성하면") {
        val (postDomainService, communityDomainService, useCase) = newUseCase()
        val community = publicCommunity(hostUserId = 1L)
        every { communityDomainService.requireActiveMember(10L, 1L) } just Runs
        every { communityDomainService.getCommunity(10L, 1L) } returns community
        every {
            postDomainService.createCommunityPost(
                userId = 1L,
                title = "공지",
                content = "내용",
                type = PostType.NOTICE,
                communityId = 10L,
                sportCategory = SportCategory.SOCCER,
                authorIsHost = true,
                communityIsPublic = true,
            )
        } returns Post.createInCommunity(
            userId = 1L,
            title = "공지",
            content = "내용",
            type = PostType.NOTICE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        )

        When("execute 를 호출하면") {
            val command = CreateCommunityPostCommand(
                userId = 1L,
                title = "공지",
                content = "내용",
                type = PostType.NOTICE,
                communityId = 10L,
            )
            val result = useCase.execute(command)

            Then("게시글이 생성되고 community 의 sportCategory·공개여부가 상속된다") {
                result.currentCommunityId shouldBe 10L
                result.currentSportCategory shouldBe SportCategory.SOCCER
                result.isGlobalListed shouldBe true
                verify(exactly = 1) { communityDomainService.requireActiveMember(10L, 1L) }
            }
        }
    }

    Given("비멤버가 PUBLIC 모임에 게시글을 작성하려 하면") {
        val (_, communityDomainService, useCase) = newUseCase()
        every { communityDomainService.requireActiveMember(10L, 2L) } throws NotCommunityMemberException(10L, 2L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                useCase.execute(
                    CreateCommunityPostCommand(
                        userId = 2L,
                        title = "잡담",
                        content = "내용",
                        type = PostType.FREE,
                        communityId = 10L,
                    ),
                )
            }
        }
    }

    Given("ACTIVE 멤버(비방장)가 NOTICE 를 작성하려 하면") {
        val (postDomainService, communityDomainService, useCase) = newUseCase()
        val community = publicCommunity(hostUserId = 1L)
        every { communityDomainService.requireActiveMember(10L, 2L) } just Runs
        every { communityDomainService.getCommunity(10L, 2L) } returns community
        every {
            postDomainService.createCommunityPost(
                userId = 2L,
                title = "공지",
                content = "내용",
                type = PostType.NOTICE,
                communityId = 10L,
                sportCategory = SportCategory.SOCCER,
                authorIsHost = false,
                communityIsPublic = true,
            )
        } throws NoticeRequiresHostException(10L, 2L)

        Then("NoticeRequiresHostException 을 던진다") {
            shouldThrow<NoticeRequiresHostException> {
                useCase.execute(
                    CreateCommunityPostCommand(
                        userId = 2L,
                        title = "공지",
                        content = "내용",
                        type = PostType.NOTICE,
                        communityId = 10L,
                    ),
                )
            }
        }
    }

    Given("PRIVATE 모임 ACTIVE 멤버가 게시글을 작성하면") {
        val (postDomainService, communityDomainService, useCase) = newUseCase()
        val community = privateCommunity(hostUserId = 1L)
        every { communityDomainService.requireActiveMember(20L, 3L) } just Runs
        every { communityDomainService.getCommunity(20L, 3L) } returns community
        every {
            postDomainService.createCommunityPost(
                userId = 3L,
                title = "제목",
                content = "내용",
                type = PostType.FREE,
                communityId = 20L,
                sportCategory = SportCategory.TENNIS,
                authorIsHost = false,
                communityIsPublic = false,
            )
        } returns Post.createInCommunity(
            userId = 3L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = false,
            communityIsPublic = false,
        )

        When("execute 를 호출하면") {
            val result = useCase.execute(
                CreateCommunityPostCommand(
                    userId = 3L,
                    title = "제목",
                    content = "내용",
                    type = PostType.FREE,
                    communityId = 20L,
                ),
            )

            Then("globalListed 가 false 로 저장된다") {
                result.isGlobalListed shouldBe false
            }
        }
    }
})
