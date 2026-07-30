package com.sportsapp.domain.common.exceptions

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class UnsupportedContentTypeException(contentType: String) : BusinessException(
    errorCode = "UNSUPPORTED_CONTENT_TYPE",
    message = "허용되지 않는 Content-Type입니다: $contentType",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
