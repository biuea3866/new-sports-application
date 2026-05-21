package com.sportsapp.domain.post

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotCommentOwnerException(commentId: Long) : BusinessException(
    errorCode = "COMMENT_ACCESS_DENIED",
    message = "Access to comment $commentId is denied",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
