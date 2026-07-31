package com.sportsapp.domain.partner.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class PartnerNotFoundException(partnerId: Long) : BusinessException(
    errorCode = "PARTNER_NOT_FOUND",
    message = "Partner(id=$partnerId) not found",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
