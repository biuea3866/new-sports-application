package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationChannelGateway
import com.sportsapp.domain.notification.SendResult
import org.springframework.stereotype.Component

@Component
class InAppChannelGateway : NotificationChannelGateway {

    override val supportedChannel: NotificationChannel = NotificationChannel.IN_APP

    override fun send(notification: Notification): SendResult {
        return SendResult(success = true, errorMessage = null)
    }
}
