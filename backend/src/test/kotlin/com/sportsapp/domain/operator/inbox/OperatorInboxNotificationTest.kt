package com.sportsapp.domain.operator.inbox

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class OperatorInboxNotificationTest : BehaviorSpec({

    Given("UNREAD 상태의 OperatorInboxNotification") {
        val notification = OperatorInboxNotification.create(
            recipientUserId = 1L,
            type = OperatorInboxNotificationType.ANOMALY,
            title = "비정상 패턴 감지",
            body = "토큰 spike 감지",
            link = "/portal/anomalies/1",
        )

        When("markRead 를 호출하면") {
            notification.markRead()

            Then("[U-01] 상태가 READ 로 전이되고 readAt 이 채워진다") {
                notification.status shouldBe OperatorInboxNotificationStatus.READ
                notification.readAt.shouldNotBeNull()
            }
        }
    }

    Given("이미 READ 상태인 OperatorInboxNotification") {
        val notification = OperatorInboxNotification.create(
            recipientUserId = 1L,
            type = OperatorInboxNotificationType.ANOMALY,
            title = "비정상 패턴 감지",
            body = "토큰 spike 감지",
            link = null,
        )
        notification.markRead()
        val firstReadAt = notification.readAt

        When("다시 markRead 를 호출하면") {
            notification.markRead()

            Then("[U-02] readAt 은 변경되지 않고 멱등하게 처리된다") {
                notification.readAt shouldBe firstReadAt
            }
        }
    }

    Given("ARCHIVED 상태의 OperatorInboxNotification 에 markRead 를 호출하는 상황") {
        val notification = OperatorInboxNotification.create(
            recipientUserId = 1L,
            type = OperatorInboxNotificationType.ANOMALY,
            title = "비정상",
            body = "내용",
            link = null,
        )
        notification.archive()

        When("markRead 를 호출하면") {
            Then("[U-03] canMarkRead 가 false 이므로 IllegalStateException 이 발생한다") {
                shouldThrow<IllegalStateException> {
                    notification.markRead()
                }
            }
        }
    }

    Given("UNREAD 상태의 OperatorInboxNotification") {
        val notification = OperatorInboxNotification.create(
            recipientUserId = 1L,
            type = OperatorInboxNotificationType.LOW_INVENTORY,
            title = "재고 부족",
            body = "SKU-001 재고 임계값 도달",
            link = null,
        )

        When("archive 를 호출하면") {
            notification.archive()

            Then("[U-04] 상태가 ARCHIVED 로 전이된다") {
                notification.status shouldBe OperatorInboxNotificationStatus.ARCHIVED
            }
        }
    }

    Given("ARCHIVED 상태의 OperatorInboxNotification") {
        val notification = OperatorInboxNotification.create(
            recipientUserId = 1L,
            type = OperatorInboxNotificationType.BOOKING_CONFLICT,
            title = "예약 충돌",
            body = "예약 충돌 발생",
            link = null,
        )
        notification.archive()

        When("다시 archive 를 호출하면") {
            Then("[U-05] IllegalStateException 이 발생한다") {
                shouldThrow<IllegalStateException> {
                    notification.archive()
                }
            }
        }
    }

    Given("link 가 null 인 OperatorInboxNotification") {
        When("create 를 호출하면") {
            val notification = OperatorInboxNotification.create(
                recipientUserId = 1L,
                type = OperatorInboxNotificationType.ANOMALY,
                title = "알림",
                body = "내용",
                link = null,
            )

            Then("[U-06] link 는 null 이고 status 는 UNREAD 로 초기화된다") {
                notification.link shouldBe null
                notification.status shouldBe OperatorInboxNotificationStatus.UNREAD
            }
        }
    }
})
