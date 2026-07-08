package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class DuplicateSeatException : BusinessException(
    errorCode = "DUPLICATE_SEAT",
    message = "Event seats must be unique by section, row and seat number"
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
