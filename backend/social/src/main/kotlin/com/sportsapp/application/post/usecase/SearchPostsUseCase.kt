package com.sportsapp.application.post.usecase

import com.sportsapp.application.post.dto.PostCriteria
import com.sportsapp.application.post.dto.PostResponse
import com.sportsapp.domain.post.service.PostDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.data.domain.Page
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 전역 피드 검색 — `GET /posts` 는 항상 전역 피드다(TDD "API 계약"). 호출자가 전달한
 * criteria 와 무관하게 globalFeedOnly 를 true 로 강제해, PRIVATE 모임 게시글이 전역
 * 종목검색으로 새는 것을 막는다(C-1).
 *
 * 작성자 표시 이름은 user 컨텍스트가 소유한다 — post 도메인이 user 를 참조하지 않도록,
 * 두 컨텍스트를 모두 아는 이 application 레이어가 페이지의 작성자 id 를 모아 한 번에 조회한다
 * (게시글 수만큼 단건 조회하는 N+1 을 만들지 않는다).
 */
@Service
class SearchPostsUseCase(
    private val postDomainService: PostDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: PostCriteria): Page<PostResponse> {
        val globalCriteria = criteria.copy(globalFeedOnly = true)
        val posts = postDomainService.search(globalCriteria.toSearchCriteria(), globalCriteria.toPageable())
        val authorNames = userDomainService.findDisplayNamesBy(posts.content.map { it.userId })
        return posts.map { PostResponse.of(it, authorNames.of(it.userId)) }
    }
}
