package com.sportsapp.domain.partner.exception
import com.sportsapp.domain.partner.entity.ApiKeyStatus

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class PartnerApiKeyInactiveException(keyId: Long?, status: ApiKeyStatus) : BusinessException(
    errorCode = "PARTNER_API_KEY_INACTIVE",
    message = "PartnerApiKey(id=$keyId) is not active: current status=$status",
) {
    override val status: ErrorStatus = ErrorStatus.UNAUTHORIZED
}
