package com.sportsapp.domain.post

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class PostDeletedException(postId: Long) : BusinessException(
    errorCode = "POST_DELETED",
    message = "Post $postId has been deleted",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
