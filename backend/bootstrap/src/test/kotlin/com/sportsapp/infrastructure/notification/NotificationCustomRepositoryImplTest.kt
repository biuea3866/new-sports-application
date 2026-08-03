package com.sportsapp.infrastructure.notification

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.notification.repository.NotificationCustomRepository
import com.sportsapp.domain.notification.entity.Notification
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.vo.NotificationPayload
import com.sportsapp.domain.notification.repository.NotificationRepository
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.infrastructure.notification.mysql.NotificationJpaRepository
import io.kotest.matchers.longs.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class NotificationCustomRepositoryImplTest(
    @Autowired private val notificationCustomRepository: NotificationCustomRepository,
    @Autowired private val notificationJpaRepository: NotificationJpaRepository,
    @Autowired private val notificationRepository: NotificationRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("userId=500 의 알림 5건이 저장된 상태에서") {
            val userId = 500L + System.nanoTime() % 1000
            repeat(5) {
                notificationJpaRepository.saveAndFlush(
                    Notification.queue(
                        userId = userId,
                        channel = NotificationChannel.IN_APP,
                        templateId = "tpl-${UUID.randomUUID()}",
                        payload = NotificationPayload(emptyMap()),
                    )
                )
            }

            When("onlyUnread=false, page=0, size=20 으로 조회하면") {
                val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = notificationCustomRepository.findByUserIdPaged(userId, NotificationChannel.IN_APP, false, pageable)

                Then("[R-01] 5건이 반환되고 createdAt desc 정렬이다") {
                    result.totalElements shouldBe 5L
                    result.content.size shouldBe 5
                    val dates = result.content.map { it.createdAt }
                    for (i in 0 until dates.size - 1) {
                        (dates[i].isAfter(dates[i + 1]) || dates[i] == dates[i + 1]) shouldBe true
                    }
                }
            }
        }

        Given("userId=600 의 알림 5건 중 2건이 읽힌 상태에서") {
            val userId = 600L + System.nanoTime() % 1000

            val notifications = (1..5).map {
                notificationJpaRepository.saveAndFlush(
                    Notification.queue(
                        userId = userId,
                        channel = NotificationChannel.IN_APP,
                        templateId = "tpl-${UUID.randomUUID()}",
                        payload = NotificationPayload(emptyMap()),
                    )
                )
            }

            notifications.take(2).forEach { notification ->
                val found = notificationJpaRepository.findById(notification.id).get()
                found.markRead()
                notificationJpaRepository.saveAndFlush(found)
            }

            When("[R-01] countUnreadByUserId 를 호출하면") {
                val count = notificationRepository.countUnreadByUserId(userId, NotificationChannel.IN_APP)

                Then("[R-01] read_at IS NULL 조건으로 3건이 반환된다") {
                    count shouldBe 3L
                }
            }

            When("onlyUnread=true 로 조회하면") {
                val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = notificationCustomRepository.findByUserIdPaged(userId, NotificationChannel.IN_APP, true, pageable)

                Then("읽지 않은 3건만 반환된다") {
                    result.totalElements shouldBe 3L
                    result.content.all { it.readAt == null } shouldBe true
                }
            }
        }

        Given("userId=700 의 알림 25건이 저장된 상태에서") {
            val userId = 700L + System.nanoTime() % 1000
            repeat(25) {
                notificationJpaRepository.saveAndFlush(
                    Notification.queue(
                        userId = userId,
                        channel = NotificationChannel.IN_APP,
                        templateId = "tpl-${UUID.randomUUID()}",
                        payload = NotificationPayload(emptyMap()),
                    )
                )
            }

            When("page=0, size=20 으로 조회하면") {
                val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = notificationCustomRepository.findByUserIdPaged(userId, NotificationChannel.IN_APP, false, pageable)

                Then("[R-02] 20건이 반환되고 totalElements 는 25 이다") {
                    result.content.size shouldBe 20
                    result.totalElements shouldBe 25L
                }
            }

            When("page=1, size=20 으로 조회하면") {
                val pageable = PageRequest.of(1, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = notificationCustomRepository.findByUserIdPaged(userId, NotificationChannel.IN_APP, false, pageable)

                Then("나머지 5건이 반환된다") {
                    result.content.size shouldBe 5
                }
            }
        }

        Given("userId=800 의 SENT 알림 1건이 read_at=null 로 저장된 상태에서") {
            val userId = 800L + System.nanoTime() % 1000
            val saved = notificationJpaRepository.saveAndFlush(
                Notification(
                    userId = userId,
                    channel = NotificationChannel.IN_APP,
                    templateId = "tpl-${UUID.randomUUID()}",
                    payload = NotificationPayload(emptyMap()),
                    status = NotificationStatus.SENT,
                    sentAt = ZonedDateTime.now(ZoneOffset.UTC),
                    readAt = null,
                    eventId = null,
                )
            )

            When("markRead 후 저장하면") {
                val found = notificationJpaRepository.findById(saved.id).get()
                found.markRead()
                notificationJpaRepository.saveAndFlush(found)

                Then("read_at 이 채워지고 countUnread 가 0 을 반환한다") {
                    val count = notificationRepository.countUnreadByUserId(userId, NotificationChannel.IN_APP)
                    count shouldBe 0L
                    val updatedReadAt = notificationJpaRepository.findById(saved.id).get().readAt
                    requireNotNull(updatedReadAt).shouldBeGreaterThanOrEqual(
                        ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(5)
                    )
                }
            }
        }

        Given("같은 사건이 IN_APP·PUSH 두 채널로 저장된 상태에서") {
            val userId = 700L + System.nanoTime() % 1000
            listOf(NotificationChannel.IN_APP, NotificationChannel.PUSH).forEach { channel ->
                notificationJpaRepository.saveAndFlush(
                    Notification.queue(
                        userId = userId,
                        channel = channel,
                        templateId = "payment-completed",
                        payload = NotificationPayload(emptyMap()),
                    )
                )
            }

            When("알림함이 IN_APP 채널로 조회하면") {
                val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt"))
                val result = notificationCustomRepository
                    .findByUserIdPaged(userId, NotificationChannel.IN_APP, false, pageable)

                Then("PUSH 발송분이 제외돼 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.single().channel shouldBe NotificationChannel.IN_APP
                }

                // 배지가 채널을 안 거르면 2가 되어 목록(1)과 어긋난다.
                Then("미읽음 배지도 같은 채널 기준이라 목록 건수와 일치한다") {
                    val badge = notificationRepository
                        .countUnreadByUserId(userId, NotificationChannel.IN_APP)
                    badge shouldBe result.totalElements
                }

                Then("채널을 지정하지 않으면 PUSH 까지 전 채널이 조회된다(MCP 발송 진단)") {
                    val allChannels = notificationCustomRepository
                        .findByUserIdPaged(userId, null, false, pageable)
                    allChannels.totalElements shouldBe 2L
                }
            }
        }

    }
}

private fun ZonedDateTime.shouldBeGreaterThanOrEqual(other: ZonedDateTime) {
    (this.isAfter(other) || this == other) shouldBe true
}
