package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.usecase.AddCommentUseCase
import com.sportsapp.application.post.usecase.DeleteCommentUseCase
import com.sportsapp.application.post.usecase.ListCommentsUseCase
import com.sportsapp.domain.post.entity.Comment
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.presentation.exception.GlobalExceptionHandler
import com.sportsapp.presentation.support.fixedPrincipalResolver
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

private const val TEST_USER_ID = 100L

/**
 * AUTH-04 — 댓글 작성·삭제는 `@AuthenticationPrincipal UserPrincipal`(non-null)로,
 * 목록 조회는 익명 브라우징을 허용하기 위해 `UserPrincipal?`(nullable)로 식별한다.
 */
class CommentApiControllerTest : BehaviorSpec({

    fun initAuditFields(entity: Any) {
        val superclass = entity.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(entity, ZonedDateTime.now())
        }
    }

    fun buildMockMvc(
        addCommentUseCase: AddCommentUseCase = mockk(),
        deleteCommentUseCase: DeleteCommentUseCase = mockk(),
        listCommentsUseCase: ListCommentsUseCase = mockk(),
        userId: Long? = TEST_USER_ID,
    ) = MockMvcBuilders.standaloneSetup(
        CommentApiController(addCommentUseCase, deleteCommentUseCase, listCommentsUseCase),
    )
        .setControllerAdvice(GlobalExceptionHandler())
        .setCustomArgumentResolvers(fixedPrincipalResolver(userId))
        .build()

    Given("로그인한 사용자의 댓글 작성 요청") {
        val addCommentUseCase = mockk<AddCommentUseCase>()
        val post = Post.create(userId = TEST_USER_ID, title = "제목", content = "내용").also { initAuditFields(it) }
        val comment = Comment.create(post, TEST_USER_ID, "댓글 내용").also { initAuditFields(it) }
        every { addCommentUseCase.execute(match { it.postId == 1L && it.userId == TEST_USER_ID }) } returns comment
        val mockMvc = buildMockMvc(addCommentUseCase = addCommentUseCase)

        When("POST /posts/1/comments 요청 시") {
            val result = mockMvc.perform(
                post("/posts/1/comments").contentType(MediaType.APPLICATION_JSON).content("""{"content":"댓글 내용"}"""),
            )

            Then("principal.id 로 댓글이 생성되고 201을 반환한다") {
                result.andExpect(status().isCreated)
                verify(exactly = 1) { addCommentUseCase.execute(match { it.userId == TEST_USER_ID }) }
            }
        }
    }

    Given("본인 댓글 삭제 요청") {
        val deleteCommentUseCase = mockk<DeleteCommentUseCase>()
        every { deleteCommentUseCase.execute(match { it.commentId == 5L && it.requestUserId == TEST_USER_ID }) } returns Unit
        val mockMvc = buildMockMvc(deleteCommentUseCase = deleteCommentUseCase)

        When("DELETE /comments/5 요청 시") {
            val result = mockMvc.perform(delete("/comments/5"))

            Then("204를 반환한다") {
                result.andExpect(status().isNoContent)
                verify(exactly = 1) { deleteCommentUseCase.execute(match { it.requestUserId == TEST_USER_ID }) }
            }
        }
    }

    Given("인증 없이 댓글 목록을 조회하면 (공개 브라우징)") {
        val listCommentsUseCase = mockk<ListCommentsUseCase>()
        every { listCommentsUseCase.execute(postId = 1L, requesterId = null, page = 0, size = 20) } returns
            PageImpl(emptyList(), PageRequest.of(0, 20), 0)
        val mockMvc = buildMockMvc(listCommentsUseCase = listCommentsUseCase, userId = null)

        When("GET /posts/1/comments 요청 시") {
            val result = mockMvc.perform(get("/posts/1/comments"))

            Then("requesterId=null 로 조회되고 200을 반환한다") {
                result.andExpect(status().isOk)
                verify(exactly = 1) { listCommentsUseCase.execute(postId = 1L, requesterId = null, page = 0, size = 20) }
            }
        }
    }
})
