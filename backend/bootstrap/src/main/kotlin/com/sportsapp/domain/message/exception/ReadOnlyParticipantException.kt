package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ReadOnlyParticipantException(
    userId: Long,
    roomId: Long,
) : BusinessException(
    errorCode = "READ_ONLY_PARTICIPANT",
    message = "User $userId is a read-only guest in room $roomId and cannot speak",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
