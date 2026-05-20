package com.sportsapp.domain.notification

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnknownTemplateException(templateId: String) : BusinessException(
    errorCode = "UNKNOWN_TEMPLATE",
    message = "Unknown notification template: $templateId",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
