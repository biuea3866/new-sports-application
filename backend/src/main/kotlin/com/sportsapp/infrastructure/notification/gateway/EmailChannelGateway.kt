package com.sportsapp.infrastructure.notification.gateway
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.gateway.NotificationChannelGateway
import com.sportsapp.domain.notification.gateway.SendResult
import org.slf4j.LoggerFactory
import org.springframework.mail.MailException
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.stereotype.Component

/**
 * SMTP 로 이메일을 발송합니다. 로컬은 MailHog(localhost:1025), 운영은 실 SMTP 계정.
 */
@Component
class EmailChannelGateway(
    private val mailSender: JavaMailSender,
    private val contactResolver: RecipientContactResolver,
    private val properties: EmailProperties,
) : NotificationChannelGateway {

    private val logger = LoggerFactory.getLogger(EmailChannelGateway::class.java)

    override val supportedChannel: NotificationChannel = NotificationChannel.EMAIL

    override fun send(notification: Notification): SendResult {
        val to = contactResolver.emailOf(notification.userId)
            ?: return SendResult(success = false, errorMessage = "no email for user ${notification.userId}")
        return try {
            val message = SimpleMailMessage().apply {
                from = properties.from
                setTo(to)
                subject = NotificationMessage.title(notification)
                text = NotificationMessage.body(notification)
            }
            mailSender.send(message)
            SendResult(success = true, errorMessage = null)
        } catch (exception: MailException) {
            logger.warn("email send failed for user {}: {}", notification.userId, exception.message)
            SendResult(success = false, errorMessage = exception.message)
        }
    }
}
