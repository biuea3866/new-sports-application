package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.gateway.SendResult
import org.springframework.stereotype.Component

@Component
class InAppChannelGateway : NotificationChannelGateway {

    override val supportedChannel: NotificationChannel = NotificationChannel.IN_APP

    override fun send(notification: Notification): SendResult {
        return SendResult(success = true, errorMessage = null)
    }
}
