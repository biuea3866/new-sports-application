package com.sportsapp.infrastructure.operator.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.entity.OperatorInboxNotificationStatus
import com.sportsapp.domain.operator.repository.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

class OperatorInboxNotificationRepositoryIntegrationTest(
    @Autowired private val repository: OperatorInboxNotificationRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("save 후 findById 라운드트립") {
            val notification = OperatorInboxNotification.create(
                recipientUserId = 100L,
                type = OperatorInboxNotificationType.ANOMALY,
                title = "비정상 감지",
                body = "내용",
                link = "/portal/anomalies/1",
            )
            val saved = repository.save(notification)

            When("findById 로 조회하면") {
                val found = repository.findById(saved.id)

                Then("[R-01] 모든 필드가 정확히 매핑되어 반환된다") {
                    found.shouldNotBeNull()
                    found.recipientUserId shouldBe 100L
                    found.type shouldBe OperatorInboxNotificationType.ANOMALY
                    found.title shouldBe "비정상 감지"
                    found.body shouldBe "내용"
                    found.link shouldBe "/portal/anomalies/1"
                    found.status shouldBe OperatorInboxNotificationStatus.UNREAD
                    found.readAt.shouldBeNull()
                    found.createdAt.shouldNotBeNull()
                }
            }
        }

        Given("userId=200 의 알림 3건 저장 (ANOMALY 2건, LOW_INVENTORY 1건)") {
            val n1 = repository.save(
                OperatorInboxNotification.create(200L, OperatorInboxNotificationType.ANOMALY, "A1", "B1", null)
            )
            val n2 = repository.save(
                OperatorInboxNotification.create(200L, OperatorInboxNotificationType.ANOMALY, "A2", "B2", null)
            )
            repository.save(
                OperatorInboxNotification.create(200L, OperatorInboxNotificationType.LOW_INVENTORY, "A3", "B3", null)
            )

            When("type=ANOMALY 필터로 페이징 조회하면") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = repository.findByRecipientPaged(200L, OperatorInboxNotificationType.ANOMALY, null, pageable)

                Then("[R-02] ANOMALY 2건만 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.all { it.type == OperatorInboxNotificationType.ANOMALY } shouldBe true
                }
            }
        }

        Given("userId=300 의 알림 2건 (UNREAD 1건, READ 1건)") {
            val unread = repository.save(
                OperatorInboxNotification.create(300L, OperatorInboxNotificationType.POLICY_VIOLATION, "P1", "B1", null)
            )
            val read = OperatorInboxNotification.create(300L, OperatorInboxNotificationType.BOOKING_CONFLICT, "P2", "B2", null)
            read.markRead()
            repository.save(read)

            When("status=UNREAD 필터로 조회하면") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = repository.findByRecipientPaged(300L, null, OperatorInboxNotificationStatus.UNREAD, pageable)

                Then("[R-03] UNREAD 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().status shouldBe OperatorInboxNotificationStatus.UNREAD
                }
            }
        }

        Given("userId=400 의 UNREAD 알림 3건이 저장된 상황") {
            repeat(3) {
                repository.save(
                    OperatorInboxNotification.create(400L, OperatorInboxNotificationType.ANOMALY, "T$it", "B$it", null)
                )
            }

            When("countUnreadByRecipientUserId 를 호출하면") {
                val count = repository.countUnreadByRecipientUserId(400L)

                Then("[R-04] 미읽음 카운트 3이 반환된다") {
                    count shouldBe 3L
                }
            }
        }

        Given("soft-delete 된 알림") {
            val notification = repository.save(
                OperatorInboxNotification.create(500L, OperatorInboxNotificationType.ANOMALY, "DEL", "B", null)
            )
            notification.softDelete(500L)
            repository.save(notification)

            When("findById 로 조회하면") {
                val found = repository.findById(notification.id)

                Then("[R-05] null 이 반환된다 (deleted_at IS NULL 필터 적용)") {
                    found.shouldBeNull()
                }
            }
        }
    }
}
