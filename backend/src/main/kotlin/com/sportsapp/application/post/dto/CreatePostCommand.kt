package com.sportsapp.application.post

data class CreatePostCommand(
    val userId: Long,
    val title: String,
    val content: String,
)
