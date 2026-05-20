package com.sportsapp.application.post

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.PostDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class AddCommentUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val addCommentUseCase = AddCommentUseCase(postDomainService)

    fun makeComment(postId: Long, userId: Long, content: String): Comment {
        val comment = Comment.create(postId = postId, userId = userId, content = content)
        val superclass = comment.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(comment, ZonedDateTime.now())
        }
        return comment
    }

    Given("[U-01] 정상 상태의 Post에 댓글 작성 시") {
        val comment = makeComment(postId = 1L, userId = 10L, content = "댓글 내용")
        every { postDomainService.addComment(postId = 1L, userId = 10L, content = "댓글 내용") } returns comment

        When("execute를 호출하면") {
            val result = addCommentUseCase.execute(AddCommentCommand(postId = 1L, userId = 10L, content = "댓글 내용"))

            Then("[U-01] CommentResponse가 반환된다") {
                result.postId shouldBe 1L
                result.userId shouldBe 10L
                result.content shouldBe "댓글 내용"
            }
        }
    }

    Given("[U-01] 미존재 Post에 댓글 작성 시") {
        every {
            postDomainService.addComment(postId = 99999L, userId = 10L, content = "댓글")
        } throws ResourceNotFoundException("Post", 99999L)

        When("execute를 호출하면") {
            Then("[U-01] ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    addCommentUseCase.execute(AddCommentCommand(postId = 99999L, userId = 10L, content = "댓글"))
                }
            }
        }
    }
})
