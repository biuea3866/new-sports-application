package com.sportsapp.domain.post.dto

import com.sportsapp.domain.post.vo.PostType

data class PostSearchCriteria(
    val type: PostType?,
    val userId: Long?,
    val keyword: String?,
)
