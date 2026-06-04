package com.sportsapp.domain.notification.exception
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus

class NotificationNotOwnedException(notificationId: Long, userId: Long) : BusinessException(
    errorCode = "NOTIFICATION_NOT_OWNED",
    message = "Notification $notificationId does not belong to user $userId",
) {
    override val status: ErrorStatus = ErrorStatus.FORBIDDEN
}
