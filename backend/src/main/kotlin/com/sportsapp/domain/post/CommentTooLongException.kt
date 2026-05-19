package com.sportsapp.domain.post

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class CommentTooLongException(length: Int) : BusinessException(
    errorCode = "COMMENT_TOO_LONG",
    message = "Comment length $length exceeds maximum 1000 characters",
) {
    override val status: ErrorStatus = ErrorStatus.BAD_REQUEST
}
