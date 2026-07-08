package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.gateway.SendResult
import com.sportsapp.infrastructure.external.ExternalRestClientFactory
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException

/**
 * Discord Incoming Webhook 발송. webhook URL 은 per-user 연락처가 아닌 고정 채널 URL이므로
 * RecipientContactResolver 를 사용하지 않는다. 실패 시 예외를 삼켜 dispatch 가 markFailed 하도록 한다
 * (기존 SmsChannelGateway 패턴).
 */
@Component
class DiscordNotificationGatewayImpl(
    restClientFactory: ExternalRestClientFactory,
    private val properties: DiscordProperties,
) : NotificationChannelGateway {

    private val restClient: RestClient = restClientFactory.create(properties.webhookUrl)
    private val logger = LoggerFactory.getLogger(DiscordNotificationGatewayImpl::class.java)

    override val supportedChannel: NotificationChannel = NotificationChannel.DISCORD

    override fun send(notification: Notification): SendResult {
        val request = DiscordWebhookRequest(username = properties.username, embeds = listOf(buildEmbed(notification)))
        return try {
            restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
            SendResult(success = true, errorMessage = null)
        } catch (exception: RestClientException) {
            logger.warn("discord send failed for notification {}: {}", notification.id, exception.message)
            SendResult(success = false, errorMessage = exception.message)
        }
    }

    private fun buildEmbed(notification: Notification): DiscordEmbed {
        val data = notification.payload.data
        val severity = data["severity"] as? String
        return DiscordEmbed(
            title = NotificationMessage.title(notification),
            description = NotificationMessage.body(notification),
            color = colorFor(severity),
            fields = buildFields(data),
        )
    }

    private fun buildFields(data: Map<String, Any>): List<DiscordEmbedField> = listOfNotNull(
        (data["source"] as? String)?.let { DiscordEmbedField(name = "source", value = it, inline = false) },
        (data["severity"] as? String)?.let { DiscordEmbedField(name = "severity", value = it, inline = false) },
        (data["env"] as? String)?.let { DiscordEmbedField(name = "env", value = it, inline = false) },
    )

    private fun colorFor(severity: String?): Int = when (severity?.uppercase()) {
        "CRITICAL" -> COLOR_CRITICAL
        "WARN" -> COLOR_WARN
        "INFO" -> COLOR_INFO
        else -> COLOR_DEFAULT
    }

    companion object {
        private const val COLOR_CRITICAL = 0xE74C3C
        private const val COLOR_WARN = 0xF1C40F
        private const val COLOR_INFO = 0x3498DB
        private const val COLOR_DEFAULT = 0x95A5A6
    }
}

data class DiscordWebhookRequest(
    val username: String,
    val embeds: List<DiscordEmbed>,
)

data class DiscordEmbed(
    val title: String,
    val description: String,
    val color: Int,
    val fields: List<DiscordEmbedField>,
)

data class DiscordEmbedField(
    val name: String,
    val value: String,
    val inline: Boolean,
)
