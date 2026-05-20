package com.sportsapp.application.post

import com.sportsapp.domain.post.Comment
import com.sportsapp.domain.post.PostDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class ListCommentsUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val listCommentsUseCase = ListCommentsUseCase(postDomainService)

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

    Given("[U-03] Post에 댓글이 3건 있는 상태에서") {
        val comments = (1..3).map { makeComment(postId = 1L, userId = it.toLong(), content = "댓글 $it") }
        val commentPage = PageImpl(comments, PageRequest.of(0, 20), 3)
        every { postDomainService.listComments(postId = 1L, page = 0, size = 20) } returns commentPage

        When("execute를 호출하면") {
            val result = listCommentsUseCase.execute(postId = 1L, page = 0, size = 20)

            Then("[U-03] 댓글 3건이 담긴 CommentPageResponse가 반환된다") {
                result.totalElements shouldBe 3
                result.content.size shouldBe 3
                result.page shouldBe 0
                result.size shouldBe 20
            }
        }
    }

    Given("[U-03] Post에 댓글이 없는 상태에서") {
        val commentPage = PageImpl(emptyList<Comment>(), PageRequest.of(0, 20), 0)
        every { postDomainService.listComments(postId = 1L, page = 0, size = 20) } returns commentPage

        When("execute를 호출하면") {
            val result = listCommentsUseCase.execute(postId = 1L, page = 0, size = 20)

            Then("[U-03] 빈 CommentPageResponse가 반환된다") {
                result.totalElements shouldBe 0
                result.content.size shouldBe 0
            }
        }
    }
})
