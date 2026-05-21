package com.sportsapp.presentation.consumer

import com.sportsapp.application.notification.EnqueueNotificationCommand
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload

data class TicketIssuedEvent(
    val ticketOrderId: String,
    val userId: Long,
    val eventTitle: String,
) {
    fun toCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.IN_APP,
        templateId = "ticket-issued",
        payload = NotificationPayload(mapOf("eventTitle" to eventTitle)),
        recipientUserId = userId,
        eventId = ticketOrderId,
    )
}
