package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ResourceNotFoundException(resource: String, id: Any) : BusinessException(
    errorCode = "RESOURCE_NOT_FOUND",
    message = "$resource with id $id not found"
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
