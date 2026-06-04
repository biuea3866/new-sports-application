package com.sportsapp.presentation.post

import com.sportsapp.application.post.CreatePostCommand
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreatePostRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val title: String,

    @field:NotBlank
    @field:Size(max = 10000)
    val content: String,
) {
    fun toCommand(userId: Long): CreatePostCommand = CreatePostCommand(
        userId = userId,
        title = title,
        content = content,
    )
}
