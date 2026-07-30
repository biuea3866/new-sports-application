package com.sportsapp.application.post

import com.sportsapp.application.post.dto.AddCommentCommand
import com.sportsapp.application.post.usecase.AddCommentUseCase
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
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
import java.time.ZonedDateTime

class AddCommentUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<PostDomainService, CommunityDomainService, AddCommentUseCase> {
        val postDomainService = mockk<PostDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(postDomainService, communityDomainService, AddCommentUseCase(postDomainService, communityDomainService))
    }

    fun makeComment(post: Post, userId: Long, content: String): Comment {
        val comment = Comment.create(post = post, userId = userId, content = content)
        val superclass = comment.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(comment, ZonedDateTime.now())
        }
        return comment
    }

    Given("전역 Post에 댓글 작성 시") {
        val (postDomainService, communityDomainService, addCommentUseCase) = newUseCase()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = makeComment(post = post, userId = 10L, content = "댓글 내용")
        every { postDomainService.getPost(1L) } returns post
        every { postDomainService.addComment(postId = 1L, userId = 10L, content = "댓글 내용") } returns comment

        When("execute를 호출하면") {
            val result = addCommentUseCase.execute(AddCommentCommand(postId = 1L, userId = 10L, content = "댓글 내용"))

            Then("CommentResponse가 반환되고 community 인가는 호출되지 않는다") {
                result.postId shouldBe post.id
                result.userId shouldBe 10L
                result.content shouldBe "댓글 내용"
                verify(exactly = 0) { communityDomainService.requireActiveMember(any(), any()) }
            }
        }
    }

    Given("미존재 Post에 댓글 작성 시") {
        val (postDomainService, _, addCommentUseCase) = newUseCase()
        every { postDomainService.getPost(99999L) } throws ResourceNotFoundException("Post", 99999L)

        When("execute를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    addCommentUseCase.execute(AddCommentCommand(postId = 99999L, userId = 10L, content = "댓글"))
                }
            }
        }
    }

    Given("PRIVATE 모임 소속 Post에 ACTIVE 멤버가 댓글을 작성하면") {
        val (postDomainService, communityDomainService, addCommentUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = false,
        )
        val comment = makeComment(post = post, userId = 5L, content = "댓글")
        every { postDomainService.getPost(2L) } returns post
        every { communityDomainService.requireActiveMember(20L, 5L) } just Runs
        every { postDomainService.addComment(postId = 2L, userId = 5L, content = "댓글") } returns comment

        When("execute를 호출하면") {
            val result = addCommentUseCase.execute(AddCommentCommand(postId = 2L, userId = 5L, content = "댓글"))

            Then("댓글이 정상 작성된다") {
                result.userId shouldBe 5L
                verify(exactly = 1) { communityDomainService.requireActiveMember(20L, 5L) }
            }
        }
    }

    Given("PRIVATE 모임 소속 Post에 비멤버가 댓글을 작성하면") {
        val (postDomainService, communityDomainService, addCommentUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 20L,
            sportCategory = SportCategory.TENNIS,
            authorIsHost = true,
            communityIsPublic = false,
        )
        every { postDomainService.getPost(3L) } returns post
        every { communityDomainService.requireActiveMember(20L, 6L) } throws NotCommunityMemberException(20L, 6L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                addCommentUseCase.execute(AddCommentCommand(postId = 3L, userId = 6L, content = "댓글"))
            }
        }
    }
})
