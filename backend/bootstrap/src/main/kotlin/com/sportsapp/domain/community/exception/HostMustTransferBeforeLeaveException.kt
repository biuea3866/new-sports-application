package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 방장(HOST)이 방장 권한을 위임하지 않고 탈퇴를 시도할 때 발생한다.
 */
class HostMustTransferBeforeLeaveException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "HOST_MUST_TRANSFER_BEFORE_LEAVE",
    message = "Host $userId of community $communityId must transfer host role before leaving",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
