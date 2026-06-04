package com.sportsapp.presentation.post

import com.sportsapp.application.post.AddCommentCommand
import com.sportsapp.application.post.AddCommentUseCase
import com.sportsapp.application.post.CommentPageResponse
import com.sportsapp.application.post.CommentResponse
import com.sportsapp.application.post.DeleteCommentCommand
import com.sportsapp.application.post.DeleteCommentUseCase
import com.sportsapp.application.post.ListCommentsUseCase
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
        @RequestHeader("X-User-Id") userId: Long,
        @Valid @RequestBody request: AddCommentRequest,
    ): ResponseEntity<CommentResponse> {
        val command = AddCommentCommand(
            postId = postId,
            userId = userId,
            content = request.content,
        )
        val response = addCommentUseCase.execute(command)
        return ResponseEntity.status(201).body(response)
    }

    @DeleteMapping("/comments/{id}")
    fun deleteComment(
        @PathVariable id: Long,
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<Unit> {
        val command = DeleteCommentCommand(commentId = id, requestUserId = userId)
        deleteCommentUseCase.execute(command)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/posts/{postId}/comments")
    fun listComments(
        @PathVariable postId: Long,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<CommentPageResponse> {
        val response = listCommentsUseCase.execute(postId = postId, page = page, size = size)
        return ResponseEntity.ok(response)
    }
}
