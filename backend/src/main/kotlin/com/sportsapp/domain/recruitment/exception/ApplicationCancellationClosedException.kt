package com.sportsapp.domain.recruitment.exception

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class ApplicationCancellationClosedException(applicationId: Long) : BusinessException(
    errorCode = "APPLICATION_CANCELLATION_CLOSED",
    message = "Application $applicationId 은(는) 신청 마감 이후라 취소할 수 없습니다.",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
