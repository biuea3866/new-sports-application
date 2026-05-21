package com.sportsapp.domain.notification

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class InvalidNotificationStateException(
    currentStatus: NotificationStatus,
) : BusinessException(
    errorCode = "INVALID_NOTIFICATION_STATE",
    message = "현재 상태($currentStatus)에서 전이할 수 없습니다.",
) {
    override val status: ErrorStatus = ErrorStatus.UNPROCESSABLE
}
