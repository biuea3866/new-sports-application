package com.sportsapp.infrastructure.operator.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.operator.service.OperatorInboxNotificationDomainService
import com.sportsapp.domain.operator.vo.OperatorInboxNotificationType
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate

/**
 * 운영 인박스 멱등을 **실 DB의 UNIQUE 제약까지** 검증한다.
 *
 * mock 단위 테스트는 `exists → save` 분기만 확인할 뿐, V63이 건
 * `uk_operator_inbox_recipient_event` 위반이 실제로 흡수되는지는 한 줄도 검증하지 못한다.
 * 리밸런싱·재처리로 같은 이벤트가 동시에 처리되면 두 경로가 모두 exists=false를 보고
 * insert하므로, 제약 위반이 리스너까지 올라가지 않는지가 핵심이다.
 */
class OperatorInboxIdempotencyIntegrationTest(
    @Autowired private val service: OperatorInboxNotificationDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val recipientUserId = 69L

    private fun record(eventId: String, recipientUserId: Long = this.recipientUserId) =
        service.createOrSkip(
            eventId = eventId,
            recipientUserId = recipientUserId,
            type = OperatorInboxNotificationType.BOOKING_RECEIVED,
            title = "신규 예약 접수",
            body = "내 시설에 예약이 접수됐습니다.",
            link = "/portal/bookings",
        )

    private fun countRows(eventId: String): Int =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM operator_inbox_notifications WHERE event_id = ?",
            Int::class.java,
            eventId,
        ) ?: 0

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE operator_inbox_notifications")
        }

        Given("같은 이벤트를 두 번 수신할 때") {
            val eventId = "evt-dup-0001"

            When("두 번 적재를 시도하면") {
                val first = record(eventId)
                val second = record(eventId)

                Then("첫 번째만 적재되고 실 DB에 행이 한 건만 남는다") {
                    first.shouldNotBeNull()
                    second.shouldBeNull()
                    countRows(eventId) shouldBe 1
                }
            }
        }

        Given("같은 이벤트를 서로 다른 수신자에게 보낼 때") {
            val eventId = "evt-multi-0001"

            When("두 수신자에게 각각 적재하면") {
                val mine = record(eventId, recipientUserId = 69L)
                val others = record(eventId, recipientUserId = 70L)

                // 멱등 범위는 (수신자, 이벤트)다 — 이벤트 단독이면 한 명이 받는 순간 나머지가 못 받는다.
                Then("두 수신자 모두 적재된다") {
                    mine.shouldNotBeNull()
                    others.shouldNotBeNull()
                    countRows(eventId) shouldBe 2
                }
            }
        }

        Given("이미 소프트 삭제된 같은 이벤트 행이 있을 때") {
            val eventId = "evt-softdeleted-0001"

            When("같은 이벤트를 다시 적재하면") {
                record(eventId)
                jdbcTemplate.update(
                    "UPDATE operator_inbox_notifications SET deleted_at = NOW(6) WHERE event_id = ?",
                    eventId,
                )
                val afterSoftDelete = record(eventId)

                // 존재 판정이 소프트 삭제를 제외하면 UNIQUE 인덱스(삭제 행 포함)와 기준이 어긋나
                // 제약 위반이 터진다 — 그 이벤트는 영영 적재 불가한 poison 상태가 된다.
                Then("제약 위반이 밖으로 새지 않고 건너뛰며 행도 늘지 않는다") {
                    afterSoftDelete.shouldBeNull()
                    countRows(eventId) shouldBe 1
                }
            }
        }
    }
}
