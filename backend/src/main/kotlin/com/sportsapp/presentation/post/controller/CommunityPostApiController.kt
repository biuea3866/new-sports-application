package com.sportsapp.presentation.post.controller

import com.sportsapp.application.post.usecase.ListCommunityPostsUseCase
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.post.dto.response.PostResponse
import org.springframework.data.domain.Page
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 모임 게시글 목록 REST 계약 (TDD "API 계약" 신규 엔드포인트, FR-2 가시성 인가).
 *
 * communities 하위 전체 경로는 SecurityConfig 상 authenticated()라(BE-08) principal이 null인
 * 경로는 실제로는 발생하지 않지만, 익명 게스트 판정 신호(`requesterId=null`)를 그대로 보존하기
 * 위해 nullable로 받는다.
 */
@RestController
@RequestMapping("/communities/{communityId}/posts")
class CommunityPostApiController(
    private val listCommunityPostsUseCase: ListCommunityPostsUseCase,
) {
    @GetMapping
    fun listCommunityPosts(
        @PathVariable communityId: Long,
        @AuthenticationPrincipal principal: UserPrincipal?,
        @RequestParam(required = false) sportCategory: SportCategory?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<Page<PostResponse>> {
        val posts = listCommunityPostsUseCase.execute(
            communityId = communityId,
            requesterId = principal?.id,
            sportCategory = sportCategory,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(posts.map { PostResponse.of(it) })
    }
}
