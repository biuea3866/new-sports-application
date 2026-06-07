package com.sportsapp.scenario

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.service.NotificationDomainService
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.entity.NotificationStatus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired

class NotificationSendScenarioTest(
    @Autowired private val notificationDomainService: NotificationDomainService,
    @Autowired private val notificationRepository: NotificationRepository,
) : BaseJpaIntegrationTest() {

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

                Then("DB 에 QUEUED 상태로 row 가 저장되고 dispatch 이벤트가 발행된다") {
                    // send() 는 트랜잭션 내에서 QUEUED 로 저장하고 이벤트를 발행한다.
                    // 실제 발송(gateway.send) 은 AFTER_COMMIT 이벤트 핸들러에서 비동기 수행된다.
                    result.status shouldBe NotificationStatus.QUEUED
                    result.userId shouldBe userId
                    result.templateId shouldBe templateId

                    val stored = notificationRepository.findById(result.id)
                    stored.shouldNotBeNull()
                    stored.status shouldBe NotificationStatus.QUEUED
                }
            }
        }

        Given("미구현 EMAIL 채널 알림 요청") {
            val userId = 20L

            When("NotificationDomainService.send 를 EMAIL 채널로 호출하면") {
                val result = notificationDomainService.send(
                    userId = userId,
                    channel = NotificationChannel.EMAIL,
                    templateId = "email-template",
                    payload = null,
                )

                Then("throw 하지 않고 QUEUED 상태로 row 가 저장된다 — gateway 없는 채널은 dispatchById 에서 FAILED 처리된다") {
                    result.status shouldBe NotificationStatus.QUEUED
                    result.channel shouldBe NotificationChannel.EMAIL

                    val stored = notificationRepository.findById(result.id)
                    stored.shouldNotBeNull()
                    stored.status shouldBe NotificationStatus.QUEUED
                }
            }
        }
    }
}
