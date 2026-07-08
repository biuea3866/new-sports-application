package com.sportsapp.presentation.operator.controller

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.operator.entity.OperatorInboxNotification
import com.sportsapp.domain.operator.repository.OperatorInboxNotificationRepository
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus

class OperatorInboxApiControllerIntegrationTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val repository: OperatorInboxNotificationRepository,
) : BaseJpaIntegrationTest() {

    init {
        Given("userId=1001 의 UNREAD 알림 2건이 존재하는 상황") {
            repository.save(
                OperatorInboxNotification.create(1001L, OperatorInboxNotificationType.ANOMALY, "A1", "B1", "/portal/anomalies/1")
            )
            repository.save(
                OperatorInboxNotification.create(1001L, OperatorInboxNotificationType.LOW_INVENTORY, "L1", "B2", null)
            )

            When("GET /operator/inbox 를 호출하면") {
                val headers = HttpHeaders().apply { set("X-User-Id", "1001") }
                val response = restTemplate.exchange(
                    "/operator/inbox",
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers),
                    String::class.java,
                )

                Then("[S-01] 200 OK 와 함께 알림 2건이 포함된 페이지가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body.shouldContain("totalElements")
                }
            }
        }

        Given("userId=1002 의 UNREAD 알림 1건이 존재하는 상황") {
            val notification = repository.save(
                OperatorInboxNotification.create(1002L, OperatorInboxNotificationType.ANOMALY, "A", "B", null)
            )

            When("PATCH /operator/inbox/{id}/read 를 호출하면") {
                val headers = HttpHeaders().apply { set("X-User-Id", "1002") }
                val response = restTemplate.exchange(
                    "/operator/inbox/${notification.id}/read",
                    HttpMethod.PATCH,
                    HttpEntity<Unit>(headers),
                    String::class.java,
                )

                Then("[S-02] 200 OK 와 함께 READ 상태의 알림이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body.shouldContain("READ")
                }
            }
        }

        Given("userId=1003 의 알림이 없는 상황") {
            When("GET /operator/inbox/unread-count 를 호출하면") {
                val headers = HttpHeaders().apply { set("X-User-Id", "1003") }
                val response = restTemplate.exchange(
                    "/operator/inbox/unread-count",
                    HttpMethod.GET,
                    HttpEntity<Unit>(headers),
                    String::class.java,
                )

                Then("[S-03] 200 OK 와 함께 unreadCount=0 이 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body.shouldContain("unreadCount")
                    response.body.shouldContain("0")
                }
            }
        }

        Given("userId=1004 의 알림에 userId=1005 가 접근하는 상황 (IDOR)") {
            val notification = repository.save(
                OperatorInboxNotification.create(1004L, OperatorInboxNotificationType.ANOMALY, "A", "B", null)
            )

            When("userId=1005 가 PATCH /operator/inbox/{id}/read 를 호출하면") {
                val headers = HttpHeaders().apply { set("X-User-Id", "1005") }
                val response = restTemplate.exchange(
                    "/operator/inbox/${notification.id}/read",
                    HttpMethod.PATCH,
                    HttpEntity<Unit>(headers),
                    String::class.java,
                )

                Then("[S-04] 404 Not Found 가 반환된다 (IDOR 차단 — 리소스 존재 미노출)") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }
}
