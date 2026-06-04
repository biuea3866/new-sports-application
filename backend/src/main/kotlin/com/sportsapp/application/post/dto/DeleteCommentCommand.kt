package com.sportsapp.application.post

data class DeleteCommentCommand(
    val commentId: Long,
    val requestUserId: Long,
)
