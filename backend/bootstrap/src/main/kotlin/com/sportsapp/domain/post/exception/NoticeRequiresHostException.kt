package com.sportsapp.domain.post.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

/**
 * 모임 소속 게시글을 NOTICE 타입으로 작성하려는 요청자가 해당 모임의 방장이 아닐 때 발생한다.
 */
class NoticeRequiresHostException(
    communityId: Long,
    userId: Long,
) : BusinessException(
    errorCode = "NOTICE_REQUIRES_HOST",
    message = "User $userId is not the host of community $communityId and cannot write a NOTICE post",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
