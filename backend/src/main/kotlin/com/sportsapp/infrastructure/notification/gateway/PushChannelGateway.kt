package com.sportsapp.infrastructure.notification

import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationChannelGateway
import com.sportsapp.domain.notification.PushTokenDomainService
import com.sportsapp.domain.notification.SendResult
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Expo Push Notifications 로 푸시를 발송합니다 (무료, 키 불필요 — 기기 토큰만 필요).
 * 실 endpoint: POST https://exp.host/--/api/v2/push/send
 */
@Component
class PushChannelGateway(
    restClientFactory: ExternalRestClientFactory,
    private val properties: PushProperties,
    private val pushTokenDomainService: PushTokenDomainService,
) : NotificationChannelGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)
    private val logger = LoggerFactory.getLogger(PushChannelGateway::class.java)

    override val supportedChannel: NotificationChannel = NotificationChannel.PUSH

    override fun send(notification: Notification): SendResult {
        val tokens = pushTokenDomainService.tokensOf(notification.userId)
        if (tokens.isEmpty()) {
            return SendResult(success = false, errorMessage = "no push token for user ${notification.userId}")
        }
        val messages = tokens.map { token ->
            ExpoPushMessage(
                to = token.token,
                title = NotificationMessage.title(notification),
                body = NotificationMessage.body(notification),
            )
        }
        return try {
            restClient.post()
                .uri("/--/api/v2/push/send")
                .headers { headers ->
                    if (properties.apiKey.isNotBlank()) {
                        headers.setBearerAuth(properties.apiKey)
                    }
                }
                .contentType(MediaType.APPLICATION_JSON)
                .body(messages)
                .retrieve()
                .toBodilessEntity()
            SendResult(success = true, errorMessage = null)
        } catch (exception: RestClientException) {
            logger.warn("push send failed for user {}: {}", notification.userId, exception.message)
            SendResult(success = false, errorMessage = exception.message)
        }
    }
}

data class ExpoPushMessage(
    val to: String,
    val title: String,
    val body: String,
)
