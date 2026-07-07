package com.sportsapp.application.post.dto

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.vo.PostType

data class CreatePostCommand(
    val userId: Long,
    val title: String,
    val content: String,
    val type: PostType = PostType.FREE,
    val sportCategory: SportCategory? = null,
)
