package com.sportsapp.presentation.consumer

import com.sportsapp.application.notification.EnqueueNotificationCommand
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload

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
