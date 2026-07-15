package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.usecase.AddCommentUseCase
import com.sportsapp.application.post.usecase.DeleteCommentUseCase
import com.sportsapp.application.post.usecase.ListCommentsUseCase
import com.sportsapp.application.post.dto.AddCommentCommand
import com.sportsapp.application.post.dto.DeleteCommentCommand
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.post.dto.request.AddCommentRequest
import com.sportsapp.presentation.post.dto.response.CommentPageResponse
import com.sportsapp.presentation.post.dto.response.CommentResponse
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
class CommentApiController(
    private val addCommentUseCase: AddCommentUseCase,
    private val deleteCommentUseCase: DeleteCommentUseCase,
    private val listCommentsUseCase: ListCommentsUseCase,
) {
    @PostMapping("/posts/{postId}/comments")
    fun addComment(
        @PathVariable postId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: AddCommentRequest,
    ): ResponseEntity<CommentResponse> {
        val command = AddCommentCommand(
            postId = postId,
            userId = principal.id,
            content = request.content,
        )
        val comment = addCommentUseCase.execute(command)
        return ResponseEntity.status(201).body(CommentResponse.of(comment))
    }

    @DeleteMapping("/comments/{id}")
    fun deleteComment(
        @PathVariable id: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Unit> {
        val command = DeleteCommentCommand(commentId = id, requestUserId = principal.id)
        deleteCommentUseCase.execute(command)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/posts/{postId}/comments")
    fun listComments(
        @PathVariable postId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CommentPageResponse> {
        val commentPage = listCommentsUseCase.execute(postId = postId, requesterId = principal?.id, page = page, size = size)
        return ResponseEntity.ok(CommentPageResponse.of(commentPage))
    }
}
