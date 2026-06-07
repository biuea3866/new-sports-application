package com.sportsapp.application.post.dto

data class CreatePostCommand(
    val userId: Long,
    val title: String,
    val content: String,
)
