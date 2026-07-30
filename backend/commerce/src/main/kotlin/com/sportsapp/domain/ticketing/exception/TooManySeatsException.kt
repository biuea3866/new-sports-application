package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class TooManySeatsException(requested: Int, limit: Int) : BusinessException(
    errorCode = "TOO_MANY_SEATS",
    message = "Seat count $requested exceeds the limit of $limit per event"
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
