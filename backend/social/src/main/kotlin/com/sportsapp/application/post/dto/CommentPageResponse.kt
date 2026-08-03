package com.sportsapp.application.post.dto

import org.springframework.data.domain.Page

data class CommentPageResponse(
    val content: List<CommentResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val page: Int,
    val size: Int,
) {
    companion object {
        fun of(page: Page<CommentResponse>): CommentPageResponse = CommentPageResponse(
            content = page.content,
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            page = page.number,
            size = page.size,
        )
    }
}
