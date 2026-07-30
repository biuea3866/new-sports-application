package com.sportsapp.application.post.dto

data class DeleteCommentCommand(
    val commentId: Long,
    val requestUserId: Long,
)
