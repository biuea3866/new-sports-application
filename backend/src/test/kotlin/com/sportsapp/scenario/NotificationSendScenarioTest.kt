package com.sportsapp.scenario

import com.sportsapp.BaseNotificationIntegrationTest
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationDomainService
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.domain.notification.UnsupportedChannelException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class NotificationSendScenarioTest(
    @Autowired private val notificationDomainService: NotificationDomainService,
    @Autowired private val notificationRepository: NotificationRepository,
) : BaseNotificationIntegrationTest() {

    init {
        Given("유효한 IN_APP 채널 알림 요청") {
            val userId = 10L
            val templateId = "match-result"
            val payload = NotificationPayload(mapOf("matchId" to "42", "result" to "WIN"))

            When("NotificationDomainService.send 를 호출하면") {
                val result = notificationDomainService.send(
                    userId = userId,
                    channel = NotificationChannel.IN_APP,
                    templateId = templateId,
                    payload = payload,
                )

                Then("[S-01] DB 에 status=SENT, sentAt 이 채워진 row 가 저장된다") {
                    result.status shouldBe NotificationStatus.SENT
                    result.sentAt.shouldNotBeNull()
                    result.userId shouldBe userId
                    result.templateId shouldBe templateId

                    val stored = notificationRepository.findById(result.id)
                    stored.shouldNotBeNull()
                    stored.status shouldBe NotificationStatus.SENT
                    stored.sentAt.shouldNotBeNull()
                }
            }
        }

        Given("미구현 EMAIL 채널 알림 요청") {
            val userId = 20L

            When("NotificationDomainService.send 를 EMAIL 채널로 호출하면") {
                Then("[S-02] UnsupportedChannelException 이 발생하고 FAILED 상태로 저장된다") {
                    shouldThrow<UnsupportedChannelException> {
                        notificationDomainService.send(
                            userId = userId,
                            channel = NotificationChannel.EMAIL,
                            templateId = "email-template",
                            payload = null,
                        )
                    }

                    val failedNotifications = notificationRepository.findByUserIdAndStatus(
                        userId,
                        NotificationStatus.FAILED,
                    )
                    failedNotifications.any { it.channel == NotificationChannel.EMAIL } shouldBe true
                }
            }
        }
    }
}
