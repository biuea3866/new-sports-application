package com.sportsapp.presentation.consumer

import com.sportsapp.application.notification.EnqueueNotificationCommand
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload

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
}
