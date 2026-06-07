package com.sportsapp.presentation.notification.worker
import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
data class BookingConfirmedEvent(
    val bookingId: String,
    val userId: Long,
    val facility: String,
) {
    fun toCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.IN_APP,
        templateId = "booking-confirmed",
        payload = NotificationPayload(mapOf("facility" to facility)),
        recipientUserId = userId,
        eventId = bookingId,
    )

    fun toPushCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.PUSH,
        templateId = "booking-confirmed",
        payload = NotificationPayload(mapOf("facility" to facility)),
        recipientUserId = userId,
        eventId = "$bookingId:push",
    )
}
