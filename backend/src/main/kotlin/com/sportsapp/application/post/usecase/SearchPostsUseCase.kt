package com.sportsapp.application.post

import com.sportsapp.domain.post.PostDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchPostsUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: PostCriteria): Page<PostResponse> {
        val pageable = criteria.toPageable()
        return postDomainService.search(criteria.toSearchCriteria(), pageable)
            .map { PostResponse.of(it) }
    }
}
