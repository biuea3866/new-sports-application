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
 * SOLAPI SMS 발송. 실발송은 발신번호 사전등록(사업자)이 필요하므로 기본은 mock 서버.
 * 실 endpoint: POST https://api.solapi.com/messages/v4/send
 */
@Component
class SmsChannelGateway(
    restClientFactory: ExternalRestClientFactory,
    private val properties: SmsProperties,
    private val contactResolver: RecipientContactResolver,
) : NotificationChannelGateway {

    private val restClient: RestClient = restClientFactory.create(properties.baseUrl)
    private val logger = LoggerFactory.getLogger(SmsChannelGateway::class.java)

    override val supportedChannel: NotificationChannel = NotificationChannel.SMS

    override fun send(notification: Notification): SendResult {
        val phone = contactResolver.phoneOf(notification)
            ?: return SendResult(success = false, errorMessage = "no phone for user ${notification.userId}")
        val text = buildText(notification)
        val request = SolapiSendRequest(SolapiMessage(to = phone, from = properties.from, text = text))
        return try {
            restClient.post()
                .uri("/messages/v4/send")
                .headers { headers ->
                    if (properties.apiKey.isNotBlank()) {
                        headers.set("Authorization", properties.apiKey)
                    }
                }
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity()
            SendResult(success = true, errorMessage = null)
        } catch (exception: RestClientException) {
            logger.warn("sms send failed for user {}: {}", notification.userId, exception.message)
            SendResult(success = false, errorMessage = exception.message)
        }
    }

    private fun buildText(notification: Notification): String {
        val title = NotificationMessage.title(notification)
        val body = NotificationMessage.body(notification)
        return if (body.isBlank()) title else "$title\n$body"
    }
}

data class SolapiSendRequest(
    val message: SolapiMessage,
)

data class SolapiMessage(
    val to: String,
    val from: String,
    val text: String,
)
