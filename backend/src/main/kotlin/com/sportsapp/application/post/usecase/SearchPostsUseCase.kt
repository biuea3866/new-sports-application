package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class SearchPostsUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: PostCriteria): Page<Post> =
        postDomainService.search(criteria.toSearchCriteria(), criteria.toPageable())
}
