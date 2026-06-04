package com.sportsapp.application.post

import com.sportsapp.domain.post.Comment
import org.springframework.data.domain.Page

data class CommentPageResponse(
    val content: List<CommentResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<Comment>): CommentPageResponse = CommentPageResponse(
            content = page.content.map { CommentResponse.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
