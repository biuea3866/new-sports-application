package com.sportsapp.presentation.post.dto.request

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class AddCommentRequest(
    @field:NotBlank
    @field:Size(max = 2000)
    val content: String,
)
