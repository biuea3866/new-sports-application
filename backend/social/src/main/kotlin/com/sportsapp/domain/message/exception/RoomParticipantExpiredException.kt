package com.sportsapp.domain.message.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class RoomParticipantExpiredException(
    userId: Long,
    roomId: Long,
) : BusinessException(
    errorCode = "ROOM_PARTICIPANT_EXPIRED",
    message = "User $userId's participation in room $roomId has expired",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
