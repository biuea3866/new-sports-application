package com.sportsapp.infrastructure.notification

import com.sportsapp.BaseNotificationIntegrationTest
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationRepository
import com.sportsapp.domain.notification.NotificationStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZoneOffset
import java.time.ZonedDateTime

class NotificationRepositoryImplTest(
    @Autowired private val notificationRepository: NotificationRepository,
) : BaseNotificationIntegrationTest() {

    init {
        Given("평탄 키-값 구조를 가진 Notification") {
            val payload = NotificationPayload(
                mapOf(
                    "title" to "테스트 알림",
                    "body" to "내용입니다",
                    "matchId" to "42",
                )
            )
            val notification = Notification.queue(
                userId = 100L,
                channel = NotificationChannel.IN_APP,
                templateId = "flat-payload",
                payload = payload,
            )

            When("저장 후 조회하면") {
                val saved = notificationRepository.save(notification)
                val found = notificationRepository.findById(saved.id)

                Then("[R-01] payload 가 동일한 키-값으로 역직렬화된다") {
                    found.shouldNotBeNull()
                    found.payload.data["title"] shouldBe "테스트 알림"
                    found.payload.data["body"] shouldBe "내용입니다"
                    found.payload.data["matchId"] shouldBe "42"
                }
            }
        }

        Given("여러 상태의 Notification 이 저장된 경우") {
            val userId = 200L
            val queuedNotification1 = Notification.queue(
                userId = userId,
                channel = NotificationChannel.IN_APP,
                templateId = "queued-1",
                payload = null,
            )
            val queuedNotification2 = Notification.queue(
                userId = userId,
                channel = NotificationChannel.PUSH,
                templateId = "queued-2",
                payload = null,
            )
            val sentNotification = Notification(
                id = 0L,
                userId = userId,
                channel = NotificationChannel.IN_APP,
                templateId = "sent-1",
                payload = NotificationPayload(emptyMap()),
                status = NotificationStatus.SENT,
                sentAt = ZonedDateTime.now(ZoneOffset.UTC),
                createdAt = ZonedDateTime.now(ZoneOffset.UTC),
            )

            When("(userId, QUEUED) 조건으로 조회하면") {
                notificationRepository.save(queuedNotification1)
                notificationRepository.save(queuedNotification2)
                notificationRepository.save(sentNotification)
                val result = notificationRepository.findByUserIdAndStatus(userId, NotificationStatus.QUEUED)

                Then("[R-02] QUEUED 상태인 2건만 반환된다") {
                    result shouldHaveSize 2
                    result.all { it.status == NotificationStatus.QUEUED } shouldBe true
                    result.all { it.userId == userId } shouldBe true
                }
            }
        }

        Given("동일 Notification 에 대해 markSent 가 동시에 호출되는 경우") {
            val notification = notificationRepository.save(
                Notification.queue(
                    userId = 300L,
                    channel = NotificationChannel.IN_APP,
                    templateId = "concurrent-test",
                    payload = null,
                )
            )

            When("두 스레드가 동시에 markSent 를 시도하면") {
                var successCount = 0
                var failCount = 0

                val threads = (1..2).map {
                    Thread {
                        try {
                            val fresh = notificationRepository.findById(notification.id)
                            fresh?.markSent()
                            fresh?.let { notificationRepository.save(it) }
                            successCount++
                        } catch (expected: Exception) {
                            failCount++
                        }
                    }
                }
                threads.forEach { it.start() }
                threads.forEach { it.join() }

                Then("[R-03] 최종 상태는 SENT 이다") {
                    val finalNotification = notificationRepository.findById(notification.id)
                    finalNotification?.status shouldBe NotificationStatus.SENT
                }
            }
        }
    }
}
