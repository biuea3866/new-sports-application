package com.sportsapp.presentation.notification.worker
import com.sportsapp.application.notification.dto.EnqueueNotificationCommand
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
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

    fun toPushCommand(): EnqueueNotificationCommand = EnqueueNotificationCommand(
        channel = NotificationChannel.PUSH,
        templateId = "ticket-issued",
        payload = NotificationPayload(mapOf("eventTitle" to eventTitle)),
        recipientUserId = userId,
        eventId = "$ticketOrderId:push",
    )
}
