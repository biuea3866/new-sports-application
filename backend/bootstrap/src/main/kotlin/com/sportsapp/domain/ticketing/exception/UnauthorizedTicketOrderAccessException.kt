package com.sportsapp.domain.ticketing.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnauthorizedTicketOrderAccessException(ticketOrderId: Long) : BusinessException(
    errorCode = "TICKET_ORDER_ACCESS_DENIED",
    message = "Access to ticket order $ticketOrderId is denied",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
