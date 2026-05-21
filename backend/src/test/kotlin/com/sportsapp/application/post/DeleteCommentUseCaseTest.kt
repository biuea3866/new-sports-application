package com.sportsapp.application.post

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.NotCommentOwnerException
import com.sportsapp.domain.post.PostDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class DeleteCommentUseCaseTest : BehaviorSpec({

    val postDomainService = mockk<PostDomainService>()
    val deleteCommentUseCase = DeleteCommentUseCase(postDomainService)

    Given("[U-02] 본인 댓글 삭제 요청 시") {
        every { postDomainService.deleteComment(commentId = 1L, requestUserId = 10L) } just runs

        When("execute를 호출하면") {
            deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 1L, requestUserId = 10L))

            Then("[U-02] deleteComment가 1회 호출된다") {
                verify(exactly = 1) { postDomainService.deleteComment(commentId = 1L, requestUserId = 10L) }
            }
        }
    }

    Given("[U-02] 타인 댓글 삭제 요청 시") {
        every {
            postDomainService.deleteComment(commentId = 1L, requestUserId = 99L)
        } throws NotCommentOwnerException(1L)

        When("execute를 호출하면") {
            Then("[U-02] NotCommentOwnerException을 던진다") {
                shouldThrow<NotCommentOwnerException> {
                    deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 1L, requestUserId = 99L))
                }
            }
        }
    }

    Given("[U-03] 미존재 댓글 삭제 요청 시") {
        every {
            postDomainService.deleteComment(commentId = 99999L, requestUserId = 10L)
        } throws ResourceNotFoundException("Comment", 99999L)

        When("execute를 호출하면") {
            Then("[U-03] ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 99999L, requestUserId = 10L))
                }
            }
        }
    }
})
