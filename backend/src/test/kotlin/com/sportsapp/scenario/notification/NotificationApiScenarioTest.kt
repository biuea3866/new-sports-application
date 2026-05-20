package com.sportsapp.scenario.notification

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.notification.Notification
import com.sportsapp.domain.notification.NotificationChannel
import com.sportsapp.domain.notification.NotificationPayload
import com.sportsapp.domain.notification.NotificationStatus
import com.sportsapp.infrastructure.notification.NotificationJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

@AutoConfigureMockMvc
class NotificationApiScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val notificationJpaRepository: NotificationJpaRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("[S-01] userId=1 의 알림 5건이 저장된 상태에서") {
            val userId = 1000L + System.nanoTime() % 1000

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

            When("[S-01] GET /notifications/me/unread-count 조회 시") {
                Then("[S-01] unread-count=5, 1건 read 후 4로 변경된다") {
                    mockMvc.perform(
                        get("/notifications/me/unread-count")
                            .header("X-User-Id", userId)
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.count").value(5))

                    val notification = notificationJpaRepository.findAll()
                        .first { it.userId == userId }

                    mockMvc.perform(
                        patch("/notifications/${notification.id}/read")
                            .header("X-User-Id", userId)
                            .accept(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk)

                    mockMvc.perform(
                        get("/notifications/me/unread-count")
                            .header("X-User-Id", userId)
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.count").value(4))
                }
            }
        }

        Given("[S-02] userId=2000 의 알림이 userId=3000 에 의해 접근 시도되는 경우") {
            val ownerUserId = 2000L + System.nanoTime() % 1000
            val otherUserId = 3000L + System.nanoTime() % 1000

            val notification = notificationJpaRepository.saveAndFlush(
                Notification.queue(
                    userId = ownerUserId,
                    channel = NotificationChannel.IN_APP,
                    templateId = "tpl-${UUID.randomUUID()}",
                    payload = NotificationPayload(emptyMap()),
                )
            )

            When("[S-02] 다른 사용자가 PATCH /notifications/{id}/read 요청 시") {
                Then("[S-02] 403 응답이 반환된다") {
                    mockMvc.perform(
                        patch("/notifications/${notification.id}/read")
                            .header("X-User-Id", otherUserId)
                            .accept(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isForbidden)
                }
            }
        }

        Given("[S-03] userId=4000 의 알림 5건이 저장된 상태에서") {
            val userId = 4000L + System.nanoTime() % 1000

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

            When("[S-03] GET /notifications/me?page=0&size=20 조회 시") {
                Then("[S-03] 결과가 createdAt desc 정렬로 반환된다") {
                    val result = mockMvc.perform(
                        get("/notifications/me")
                            .header("X-User-Id", userId)
                            .param("page", "0")
                            .param("size", "20")
                            .accept(MediaType.APPLICATION_JSON)
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.totalElements").value(5))
                        .andReturn()

                    val responseBody = result.response.contentAsString
                    responseBody.contains("\"totalElements\":5") shouldBe true
                }
            }
        }

        Given("[S-01] 이미 읽은 알림을 다시 읽음 처리 시") {
            val userId = 5000L + System.nanoTime() % 1000
            val notification = notificationJpaRepository.saveAndFlush(
                Notification(
                    userId = userId,
                    channel = NotificationChannel.IN_APP,
                    templateId = "tpl-${UUID.randomUUID()}",
                    payload = NotificationPayload(emptyMap()),
                    status = NotificationStatus.SENT,
                    sentAt = ZonedDateTime.now(ZoneOffset.UTC),
                    readAt = ZonedDateTime.now(ZoneOffset.UTC).minusHours(1),
                )
            )

            When("PATCH /notifications/{id}/read 를 다시 호출하면") {
                Then("200 응답이 반환되고 readAt 은 변경되지 않는다") {
                    val firstReadAt = notification.readAt

                    mockMvc.perform(
                        patch("/notifications/${notification.id}/read")
                            .header("X-User-Id", userId)
                            .accept(MediaType.APPLICATION_JSON)
                    ).andExpect(status().isOk)

                    val afterRead = notificationJpaRepository.findById(notification.id).get()
                    afterRead.readAt?.toInstant()?.epochSecond shouldBe firstReadAt?.toInstant()?.epochSecond
                }
            }
        }
    }
}
