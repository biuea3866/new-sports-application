package com.sportsapp.domain.message

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotRoomParticipantException(
    userId: Long,
    roomId: Long,
) : BusinessException(
    errorCode = "NOT_ROOM_PARTICIPANT",
    message = "User $userId is not a participant of room $roomId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
