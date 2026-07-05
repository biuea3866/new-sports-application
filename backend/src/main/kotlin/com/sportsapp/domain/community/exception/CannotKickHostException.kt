package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 강퇴 대상이 방장(HOST) 본인일 때 발생한다 (TDD "상태 전이 표 — CommunityMember" 거부 사유).
 */
class CannotKickHostException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "CANNOT_KICK_HOST",
    message = "Host $userId of community $communityId cannot be kicked",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
