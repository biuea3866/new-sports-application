package com.sportsapp.application.post

import com.sportsapp.application.post.usecase.ListCommentsUseCase

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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class ListCommentsUseCaseTest : BehaviorSpec({

    fun newUseCase(): Triple<PostDomainService, CommunityDomainService, ListCommentsUseCase> {
        val postDomainService = mockk<PostDomainService>()
        val communityDomainService = mockk<CommunityDomainService>()
        return Triple(postDomainService, communityDomainService, ListCommentsUseCase(postDomainService, communityDomainService))
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

    Given("[U-03] 전역 Post에 댓글이 3건 있는 상태에서") {
        val (postDomainService, communityDomainService, listCommentsUseCase) = newUseCase()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comments = (1..3).map { makeComment(post = post, userId = it.toLong(), content = "댓글 $it") }
        val commentPage = PageImpl(comments, PageRequest.of(0, 20), 3)
        every { postDomainService.getPost(1L) } returns post
        every { postDomainService.listComments(postId = 1L, page = 0, size = 20) } returns commentPage

        When("execute를 호출하면") {
            val result = listCommentsUseCase.execute(postId = 1L, page = 0, size = 20)

            Then("[U-03] 댓글 3건이 담긴 Page가 반환되고 community 인가는 호출되지 않는다") {
                result.totalElements shouldBe 3
                result.content.size shouldBe 3
                result.number shouldBe 0
                result.size shouldBe 20
                verify(exactly = 0) { communityDomainService.getCommunity(any(), any()) }
            }
        }
    }

    Given("[U-03] 전역 Post에 댓글이 없는 상태에서") {
        val (postDomainService, _, listCommentsUseCase) = newUseCase()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val commentPage = PageImpl(emptyList<Comment>(), PageRequest.of(0, 20), 0)
        every { postDomainService.getPost(1L) } returns post
        every { postDomainService.listComments(postId = 1L, page = 0, size = 20) } returns commentPage

        When("execute를 호출하면") {
            val result = listCommentsUseCase.execute(postId = 1L, page = 0, size = 20)

            Then("[U-03] 빈 CommentPageResponse가 반환된다") {
                result.totalElements shouldBe 0
                result.content.size shouldBe 0
            }
        }
    }

    Given("PUBLIC 모임 소속 Post의 댓글을 비멤버가 조회하면") {
        val (postDomainService, communityDomainService, listCommentsUseCase) = newUseCase()
        val post = Post.createInCommunity(
            userId = 1L,
            title = "제목",
            content = "내용",
            type = PostType.FREE,
            communityId = 10L,
            sportCategory = SportCategory.SOCCER,
            authorIsHost = true,
            communityIsPublic = true,
        )
        val commentPage = PageImpl(emptyList<Comment>(), PageRequest.of(0, 20), 0)
        every { postDomainService.getPost(2L) } returns post
        every { communityDomainService.getCommunity(10L, 9L) } returns mockk(relaxed = true)
        every { postDomainService.listComments(postId = 2L, page = 0, size = 20) } returns commentPage

        When("execute를 requesterId=9L로 호출하면") {
            val result = listCommentsUseCase.execute(postId = 2L, requesterId = 9L, page = 0, size = 20)

            Then("정상 통과한다") {
                result.totalElements shouldBe 0
            }
        }
    }

    Given("PRIVATE 모임 소속 Post의 댓글을 비멤버가 조회하면") {
        val (postDomainService, communityDomainService, listCommentsUseCase) = newUseCase()
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
        every { communityDomainService.getCommunity(20L, 9L) } throws NotCommunityMemberException(20L, 9L)

        Then("NotCommunityMemberException 을 던진다") {
            shouldThrow<NotCommunityMemberException> {
                listCommentsUseCase.execute(postId = 3L, requesterId = 9L, page = 0, size = 20)
            }
        }
    }
})
