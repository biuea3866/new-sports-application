package com.sportsapp.application.post.dto

data class AddCommentCommand(
    val postId: Long,
    val userId: Long,
    val content: String,
)
