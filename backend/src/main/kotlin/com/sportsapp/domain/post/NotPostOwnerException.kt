package com.sportsapp.domain.post

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotPostOwnerException(userId: Long) : BusinessException(
    errorCode = "NOT_POST_OWNER",
    message = "User $userId is not the owner of this post",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
