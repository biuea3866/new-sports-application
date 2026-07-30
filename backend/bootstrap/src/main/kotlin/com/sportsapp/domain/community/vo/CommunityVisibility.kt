package com.sportsapp.domain.community.vo

/**
 * 커뮤니티 공개 여부 (TDD FR-1). PUBLIC은 즉시 가입, PRIVATE은 방장 승인 후 가입된다.
 */
enum class CommunityVisibility {
    PUBLIC,
    PRIVATE,
}
