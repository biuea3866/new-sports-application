package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 이미 ACTIVE 또는 PENDING_APPROVAL 상태인 사용자가 동일 커뮤니티에 다시 가입을 시도할 때
 * 발생한다 (리뷰 p2-① — 중복 가입 시 `community_members` UNIQUE 제약 500 대신 명시적 409).
 */
class AlreadyCommunityMemberException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "ALREADY_COMMUNITY_MEMBER",
    message = "User $userId is already a member of community $communityId",
) {
    override val status: ErrorStatus = ErrorStatus.CONFLICT
}
