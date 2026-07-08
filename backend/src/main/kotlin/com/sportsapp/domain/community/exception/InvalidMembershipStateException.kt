package com.sportsapp.domain.community.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.community.vo.MembershipStatus

/**
 * [MembershipStatus.canTransitTo]가 허용하지 않는 상태 전이를 시도할 때 발생한다.
 */
class InvalidMembershipStateException(
    from: MembershipStatus,
    to: MembershipStatus,
) : BusinessException(
    errorCode = "INVALID_MEMBERSHIP_STATE",
    message = "Cannot transit community membership from $from to $to",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
