package com.sportsapp.domain.notification.exception
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotificationNotFoundException(notificationId: Long) : BusinessException(
    errorCode = "NOTIFICATION_NOT_FOUND",
    message = "Notification not found: $notificationId",
) {
    override val status: ErrorStatus = ErrorStatus.NOT_FOUND
}
