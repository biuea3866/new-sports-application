package com.sportsapp.application.post.dto

import com.sportsapp.domain.post.vo.PostType

data class CreateCommunityPostCommand(
    val userId: Long,
    val title: String,
    val content: String,
    val type: PostType,
    val communityId: Long,
)
