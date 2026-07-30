package com.sportsapp.domain.post.dto

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.post.vo.PostType

data class PostSearchCriteria(
    val type: PostType?,
    val userId: Long?,
    val keyword: String?,
    val communityId: Long? = null,
    val sportCategory: SportCategory? = null,
    val globalFeedOnly: Boolean = false,
)
