package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.domain.post.entity.Post
import com.sportsapp.domain.post.service.PostDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전역 피드 검색 — `GET /posts` 는 항상 전역 피드다(TDD "API 계약"). 호출자가 전달한
 * criteria 와 무관하게 globalFeedOnly 를 true 로 강제해, PRIVATE 모임 게시글이 전역
 * 종목검색으로 새는 것을 막는다(C-1).
 */
@Service
class SearchPostsUseCase(
    private val postDomainService: PostDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: PostCriteria): Page<Post> {
        val globalCriteria = criteria.copy(globalFeedOnly = true)
        return postDomainService.search(globalCriteria.toSearchCriteria(), globalCriteria.toPageable())
    }
}
