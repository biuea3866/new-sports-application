package com.sportsapp.application.post

import com.sportsapp.application.post.dto.DeleteCommentCommand
import com.sportsapp.application.post.usecase.DeleteCommentUseCase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.post.exception.NotCommentOwnerException
import com.sportsapp.domain.post.service.CommentDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

class DeleteCommentUseCaseTest : BehaviorSpec({

    val commentDomainService = mockk<CommentDomainService>()
    val deleteCommentUseCase = DeleteCommentUseCase(commentDomainService)

    Given("본인 댓글 삭제 요청 시") {
        every { commentDomainService.deleteComment(commentId = 1L, requestUserId = 10L) } just runs

        When("execute를 호출하면") {
            deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 1L, requestUserId = 10L))

            Then("deleteComment가 1회 호출된다") {
                verify(exactly = 1) { commentDomainService.deleteComment(commentId = 1L, requestUserId = 10L) }
            }
        }
    }

    Given("타인 댓글 삭제 요청 시") {
        every {
            commentDomainService.deleteComment(commentId = 1L, requestUserId = 99L)
        } throws NotCommentOwnerException(1L)

        When("execute를 호출하면") {
            Then("NotCommentOwnerException을 던진다") {
                shouldThrow<NotCommentOwnerException> {
                    deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 1L, requestUserId = 99L))
                }
            }
        }
    }

    Given("미존재 댓글 삭제 요청 시") {
        every {
            commentDomainService.deleteComment(commentId = 99999L, requestUserId = 10L)
        } throws ResourceNotFoundException("Comment", 99999L)

        When("execute를 호출하면") {
            Then("ResourceNotFoundException을 던진다") {
                shouldThrow<ResourceNotFoundException> {
                    deleteCommentUseCase.execute(DeleteCommentCommand(commentId = 99999L, requestUserId = 10L))
                }
            }
        }
    }
})
