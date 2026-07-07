package com.sportsapp.application.post

import com.sportsapp.application.post.usecase.GetPostUseCase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.post.vo.PostType
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class GetPostUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<PostDomainService, CommunityDomainService, GetPostUseCase> {
        val postDomainService = mockk<PostDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(postDomainService, communityDomainService, GetPostUseCase(postDomainService, communityDomainService))
    }

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    Given("[U-02] 존재하지 않는 postId로 조회하면") {
        val (postDomainService, _, getPostUseCase) = newUseCase()
        every { postDomainService.getDetail(99999L) } throws ResourceNotFoundException("Post", 99999L)

        When("execute를 호출하면") {
            Then("[U-02] PostNotFoundException(ResourceNotFoundException)을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    getPostUseCase.execute(99999L)
                }
            }
        }
    }

    Given("[U-03] 댓글 3건이 있는 전역 Post를 조회하면") {
        val (postDomainService, communityDomainService, getPostUseCase) = newUseCase()
        val post = Post.create(userId = 1L, title = "제목", content = "내용", type = PostType.FREE)
            .also { initAuditFields(it) }
        val comments = (1..3).map {
            Comment.create(post = post, userId = it.toLong(), content = "댓글 $it")
                .also { comment -> initAuditFields(comment) }
        }
        every { postDomainService.getDetail(1L) } returns Pair(post, comments)

        When("execute를 호출하면") {
            val result = getPostUseCase.execute(1L)

            Then("[U-03] 댓글 3건이 포함된 Pair가 반환되고 community 인가는 호출되지 않는다") {
                result.second.size shouldBe 3
                verify(exactly = 0) { communityDomainService.getCommunity(any(), any()) }
            }
        }
    }

    Given("PUBLIC 모임 소속 게시글을 비멤버가 조회하면") {
        val (postDomainService, communityDomainService, getPostUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        ).also { initAuditFields(it) }
        val community = Community.create(
            name = "모임",
            description = null,
            visibility = CommunityVisibility.PUBLIC,
            sportCategory = SportCategory.SOCCER,
            hostUserId = 1L,
        )
        every { postDomainService.getDetail(1L) } returns Pair(post, emptyList())
        every { communityDomainService.getCommunity(10L, 2L) } returns community

        When("execute를 요청자 2L로 호출하면") {
            val result = getPostUseCase.execute(postId = 1L, requesterId = 2L)

            Then("정상 통과한다") {
                result.first.currentCommunityId shouldBe 10L
            }
        }
    }

    Given("PRIVATE 모임 소속 게시글을 비멤버가 조회하면") {
        val (postDomainService, communityDomainService, getPostUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = false,
        ).also { initAuditFields(it) }
        every { postDomainService.getDetail(2L) } returns Pair(post, emptyList())
        every { communityDomainService.getCommunity(20L, 2L) } throws NotCommunityMemberException(20L, 2L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                getPostUseCase.execute(postId = 2L, requesterId = 2L)
            }
        }
    }

    Given("PRIVATE 모임 소속 게시글을 requesterId 없이(비로그인) 조회하면") {
        val (postDomainService, communityDomainService, getPostUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 30L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = false,
        ).also { initAuditFields(it) }
        every { postDomainService.getDetail(3L) } returns Pair(post, emptyList())
        every { communityDomainService.getCommunity(30L, 0L) } throws NotCommunityMemberException(30L, 0L)

        Then("게스트 sentinel 로 조회해 NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                getPostUseCase.execute(postId = 3L, requesterId = null)
            }
        }
    }
})
