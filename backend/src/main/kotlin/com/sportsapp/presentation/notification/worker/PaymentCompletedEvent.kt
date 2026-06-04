package com.sportsapp.presentation.notification.worker
import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
data class PaymentCompletedEvent(
    val paymentId: String,
    val userId: Long,
    val amount: Long,
) {
    fun toCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.IN_APP,
        templateId = "payment-completed",
        payload = NotificationPayload(mapOf("amount" to amount.toString())),
        recipientUserId = userId,
        eventId = paymentId,
    )

    fun toPushCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.PUSH,
        templateId = "payment-completed",
        payload = NotificationPayload(mapOf("amount" to amount.toString())),
        recipientUserId = userId,
        eventId = "$paymentId:push",
    )
}
