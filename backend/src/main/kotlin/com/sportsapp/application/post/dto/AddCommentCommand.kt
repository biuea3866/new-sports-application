package com.sportsapp.application.post

data class AddCommentCommand(
    val postId: Long,
    val userId: Long,
    val content: String,
)
