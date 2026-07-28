package com.sportsapp.infrastructure.notification.gateway

import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.mail.SimpleMailMessage
import org.springframework.mail.javamail.JavaMailSender

/**
 * EmailChannelGateway — RecipientContactResolver가 수신처를 찾지 못하면
 * 발송을 시도하지 않는다 (엣지 케이스).
 */
class EmailChannelGatewayTest : BehaviorSpec({

    val mailSender = mockk<JavaMailSender>()
    val contactResolver = mockk<RecipientContactResolver>()
    val properties = EmailProperties()
    val gateway = EmailChannelGateway(mailSender, contactResolver, properties)

    Given("수신처(이메일)를 찾지 못한 사용자에게 알림을 발송하면") {
        val notification = Notification.queue(
            userId = 999L,
            channel = NotificationChannel.EMAIL,
            templateId = "template-1",
            payload = NotificationPayload(emptyMap()),
        )
        every { contactResolver.emailOf(999L) } returns null

        When("send를 호출하면") {
            val result = gateway.send(notification)

            Then("실패 결과를 반환하고 메일 발송을 시도하지 않는다") {
                result.success.shouldBeFalse()
                verify(exactly = 1) { contactResolver.emailOf(999L) }
                verify(exactly = 0) { mailSender.send(any<SimpleMailMessage>()) }
                confirmVerified(mailSender)
            }
        }
    }
})
