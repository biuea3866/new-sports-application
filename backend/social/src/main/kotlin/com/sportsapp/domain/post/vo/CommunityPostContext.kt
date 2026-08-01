package com.sportsapp.domain.post.vo

import com.sportsapp.domain.common.vo.SportCategory

/**
 * 모임 소속 게시글 생성에 필요한 community 인가 결과를 묶은 값 객체 (R1).
 * domain.post 는 domain.community 를 참조하지 않고, application 이 계산한
 * primitive 값들을 이 값 객체로만 전달받는다.
 */
data class CommunityPostContext(
    val communityId: Long,
    val sportCategory: SportCategory?,
    val authorIsHost: Boolean,
    val communityIsPublic: Boolean,
)
