package com.sportsapp.domain.partner.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class PartnerSuspendedException(partnerId: Long?) : BusinessException(
    errorCode = "PARTNER_SUSPENDED",
    message = "Partner(id=$partnerId) is suspended",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
