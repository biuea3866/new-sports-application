package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 커뮤니티 멤버십 범위 조회 인가 실패 (TDD FR-13 ②).
 *
 * `GET /communities/{id}/members`, 비공개 커뮤니티 상세 `GET /communities/{id}`는
 * 요청자가 해당 커뮤니티의 ACTIVE 멤버가 아니면 이 예외로 거부된다. 컨텍스트 방(contextType=COMMUNITY)
 * 참여자일 뿐인 게스트는 `community_members`에 ACTIVE 레코드가 없어 동일하게 거부된다.
 */
class NotCommunityMemberException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "NOT_COMMUNITY_MEMBER",
    message = "User $userId is not an active member of community $communityId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
