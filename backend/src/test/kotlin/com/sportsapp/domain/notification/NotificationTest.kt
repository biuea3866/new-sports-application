package com.sportsapp.domain.notification

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class NotificationTest : BehaviorSpec({

    Given("QUEUED 상태의 Notification") {
        val notification = Notification.queue(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "welcome",
            payload = NotificationPayload(mapOf("title" to "안녕하세요")),
        )

        When("markSent 를 호출하면") {
            notification.markSent()

            Then("[U-01] 상태가 SENT 로 전이되고 sentAt 이 채워진다") {
                notification.status shouldBe NotificationStatus.SENT
                notification.sentAt.shouldNotBeNull()
            }
        }
    }

    Given("SENT 상태의 Notification") {
        val notification = Notification(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "welcome",
            payload = NotificationPayload(mapOf("title" to "안녕하세요")),
            status = NotificationStatus.SENT,
            sentAt = java.time.ZonedDateTime.now(),
            readAt = null,
            eventId = null,
        )

        When("markSent 를 호출하면") {
            Then("[U-02] InvalidNotificationStateException 이 발생한다") {
                shouldThrow<InvalidNotificationStateException> {
                    notification.markSent()
                }
            }
        }
    }

    Given("FAILED 상태의 Notification") {
        val notification = Notification(
            userId = 1L,
            channel = NotificationChannel.IN_APP,
            templateId = "welcome",
            payload = NotificationPayload(mapOf()),
            status = NotificationStatus.FAILED,
            sentAt = null,
            readAt = null,
            eventId = null,
        )

        When("markSent 를 호출하면") {
            Then("[U-03] InvalidNotificationStateException 이 발생한다") {
                shouldThrow<InvalidNotificationStateException> {
                    notification.markSent()
                }
            }
        }
    }

    Given("payload 가 null 인 경우") {
        When("Notification.queue 로 생성하면") {
            val notification = Notification.queue(
                userId = 1L,
                channel = NotificationChannel.IN_APP,
                templateId = "test",
                payload = null,
            )

            Then("[U-04] payload 가 빈 NotificationPayload 로 초기화된다") {
                notification.payload shouldBe NotificationPayload(emptyMap())
            }
        }
    }

    Given("payload 가 존재하는 경우") {
        When("Notification.queue 로 생성하면") {
            val payload = NotificationPayload(mapOf("key" to "value"))
            val notification = Notification.queue(
                userId = 2L,
                channel = NotificationChannel.PUSH,
                templateId = "push-test",
                payload = payload,
            )

            Then("[U-05] 전달된 payload 가 그대로 유지된다") {
                notification.payload shouldBe payload
            }
        }
    }
})
