package com.sportsapp.scenario.notification

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.notification.vo.NotificationChannel
import com.sportsapp.domain.notification.entity.NotificationStatus
import com.sportsapp.infrastructure.notification.mysql.NotificationJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.test.context.support.WithMockUser
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@AutoConfigureMockMvc
class AdminNotificationApiScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val notificationJpaRepository: NotificationJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    init {
        Given("[S-01] 유효한 templateId 와 payload 로 POST /admin/notifications/send 요청") {
            val userId = 9001L
            val requestBody = mapOf(
                "userId" to userId,
                "channel" to "IN_APP",
                "templateId" to "payment-completed",
                "payload" to mapOf("amount" to 50000),
            )

            When("관리자 권한으로 API 를 호출하면") {
                Then("[S-01] 200 응답이 반환되고 DB 에 SENT 상태 알림이 저장된다") {
                    mockMvc.perform(
                        post("/admin/notifications/send")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody))
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.userId").value(userId))
                        .andExpect(jsonPath("$.templateId").value("payment-completed"))
                        .andExpect(jsonPath("$.status").value("SENT"))

                    val stored = notificationJpaRepository.findAll()
                        .filter { it.userId == userId && it.templateId == "payment-completed" }
                    stored.size shouldBe 1
                    stored.first().status shouldBe NotificationStatus.SENT
                }
            }
        }

        Given("[S-02] 존재하지 않는 templateId 로 POST /admin/notifications/send 요청") {
            val requestBody = mapOf(
                "userId" to 9002L,
                "channel" to "IN_APP",
                "templateId" to "non-existent-template",
                "payload" to emptyMap<String, Any>(),
            )

            When("관리자 권한으로 API 를 호출하면") {
                Then("[S-02] 404 응답이 반환된다") {
                    mockMvc.perform(
                        post("/admin/notifications/send")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody))
                    ).andExpect(status().isNotFound)
                }
            }
        }

        Given("[S-03] placeholder 가 없는 templateId 에 빈 payload 로 POST /admin/notifications/send 요청") {
            val userId = 9004L
            val requestBody = mapOf(
                "userId" to userId,
                "channel" to "IN_APP",
                "templateId" to "payment-completed",
                "payload" to emptyMap<String, Any>(),
            )

            When("관리자 권한으로 API 를 호출하면") {
                Then("[S-03] 200 응답이 반환되고 DB 에 SENT 상태 알림이 저장된다") {
                    mockMvc.perform(
                        post("/admin/notifications/send")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody))
                    )
                        .andExpect(status().isOk)
                        .andExpect(jsonPath("$.userId").value(userId))
                        .andExpect(jsonPath("$.status").value("SENT"))

                    val stored = notificationJpaRepository.findAll()
                        .filter { it.userId == userId }
                    stored.size shouldBe 1
                    stored.first().status shouldBe NotificationStatus.SENT
                }
            }
        }

        Given("[R-01] IN_APP 채널로 정상 발송 요청 후") {
            val userId = 9003L
            val requestBody = mapOf(
                "userId" to userId,
                "channel" to "IN_APP",
                "templateId" to "payment-completed",
                "payload" to mapOf("amount" to 10000),
            )

            When("DB 를 조회하면") {
                Then("[R-01] status=SENT, sentAt 이 채워진 Notification row 가 저장된다") {
                    mockMvc.perform(
                        post("/admin/notifications/send")
                            .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("admin").roles("ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(requestBody))
                    ).andExpect(status().isOk)

                    val notifications = notificationJpaRepository.findAll()
                        .filter { it.userId == userId }
                    notifications.size shouldBe 1
                    val notification = notifications.first()
                    notification.status shouldBe NotificationStatus.SENT
                    notification.sentAt shouldBe notification.sentAt
                    notification.channel shouldBe NotificationChannel.IN_APP
                }
            }
        }
    }
}
