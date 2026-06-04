package com.sportsapp.application.post.dto

import com.sportsapp.domain.post.dto.PostSearchCriteria
import com.sportsapp.domain.post.vo.PostType
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Sort

data class PostCriteria(
    val type: PostType?,
    val userId: Long?,
    val keyword: String?,
    val page: Int,
    val size: Int,
) {
    companion object {
        const val MAX_PAGE_SIZE = 100
    }

    fun toPageable(): Pageable {
        val cappedSize = minOf(size, MAX_PAGE_SIZE)
        return PageRequest.of(page, cappedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
    }

    fun toSearchCriteria(): PostSearchCriteria = PostSearchCriteria(
        type = type,
        userId = userId,
        keyword = keyword?.takeIf { it.isNotBlank() },
    )
}
