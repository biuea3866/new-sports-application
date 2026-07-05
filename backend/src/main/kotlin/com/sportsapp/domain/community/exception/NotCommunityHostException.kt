package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 방장 전용 조작(승인·강퇴·위임)을 방장이 아닌 사용자가 요청할 때 발생한다.
 */
class NotCommunityHostException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "NOT_COMMUNITY_HOST",
    message = "User $userId is not the host of community $communityId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
