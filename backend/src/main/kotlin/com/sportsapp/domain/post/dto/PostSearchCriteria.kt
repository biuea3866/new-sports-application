package com.sportsapp.domain.post

data class PostSearchCriteria(
    val type: PostType?,
    val userId: Long?,
    val keyword: String?,
)
