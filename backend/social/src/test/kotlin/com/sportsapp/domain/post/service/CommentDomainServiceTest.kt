package com.sportsapp.domain.post.service

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.exception.NotCommentOwnerException
import com.sportsapp.domain.post.repository.CommentRepository
import com.sportsapp.domain.post.repository.PostRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class CommentDomainServiceTest : BehaviorSpec({

    fun newService(): Triple<PostRepository, CommentRepository, CommentDomainService> {
        val postRepository = mockk<PostRepository>()
        val commentRepository = mockk<CommentRepository>()
        return Triple(postRepository, commentRepository, CommentDomainService(postRepository, commentRepository))
    }

    Given("존재하는 Post 에 댓글을 작성하면") {
        val (postRepository, commentRepository, service) = newService()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        every { postRepository.findById(1L) } returns post
        every { commentRepository.save(any()) } answers { firstArg() }

        When("addComment 를 호출하면") {
            val comment = service.addComment(postId = 1L, userId = 2L, content = "댓글 내용")

            Then("Post 에 귀속된 댓글이 저장된다") {
                comment.userId shouldBe 2L
                comment.content shouldBe "댓글 내용"
                verify(exactly = 1) { commentRepository.save(any()) }
            }
        }
    }

    Given("존재하지 않는 Post 에 댓글을 작성하면") {
        val (postRepository, _, service) = newService()
        every { postRepository.findById(999L) } returns null

        Then("ResourceNotFoundException 을 던진다") {
            shouldThrow<ResourceNotFoundException> {
                service.addComment(postId = 999L, userId = 2L, content = "댓글")
            }
        }
    }

    Given("작성자 본인이 댓글을 삭제하면") {
        val (_, commentRepository, service) = newService()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = Comment.create(post = post, userId = 10L, content = "댓글")
        every { commentRepository.findById(1L) } returns comment
        every { commentRepository.save(any()) } answers { firstArg() }

        When("deleteComment 를 호출하면") {
            service.deleteComment(commentId = 1L, requestUserId = 10L)

            Then("softDelete 되어 저장된다") {
                verify(exactly = 1) { commentRepository.save(comment) }
            }
        }
    }

    Given("작성자가 아닌 사용자가 댓글을 삭제하려 하면") {
        val (_, commentRepository, service) = newService()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = Comment.create(post = post, userId = 10L, content = "댓글")
        every { commentRepository.findById(1L) } returns comment

        Then("NotCommentOwnerException 을 던진다") {
            shouldThrow<NotCommentOwnerException> {
                service.deleteComment(commentId = 1L, requestUserId = 99L)
            }
            verify(exactly = 0) { commentRepository.save(any()) }
        }
    }

    Given("존재하지 않는 댓글을 삭제하려 하면") {
        val (_, commentRepository, service) = newService()
        every { commentRepository.findById(9999L) } returns null

        Then("ResourceNotFoundException 을 던진다") {
            shouldThrow<ResourceNotFoundException> {
                service.deleteComment(commentId = 9999L, requestUserId = 10L)
            }
        }
    }

    Given("Post 의 댓글 목록을 페이지 조회하면") {
        val (_, commentRepository, service) = newService()
        val post = Post.create(userId = 1L, title = "제목", content = "내용")
        val comment = Comment.create(post = post, userId = 10L, content = "댓글")
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "createdAt"))
        every { commentRepository.findPageByPostId(1L, pageable) } returns PageImpl(listOf(comment))

        When("listComments 를 호출하면") {
            val page = service.listComments(postId = 1L, page = 0, size = 20)

            Then("commentRepository 가 반환한 페이지를 그대로 반환한다") {
                page.content.size shouldBe 1
                page.content.first() shouldBe comment
            }
        }
    }
})
