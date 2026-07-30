package com.sportsapp.infrastructure.partner.audit

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import io.kotest.assertions.nondeterministic.eventually
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime
import kotlin.time.Duration.Companion.seconds

/**
 * @Async 프록시 + 전용 Executor(partnerAuditExecutor) 배선이 실제로 동작해
 * partner_audit_log에 비동기 적재되는지 확인하는 통합 테스트.
 */
class AsyncPartnerActivityRecorderIntegrationTest(
    @Autowired private val partnerActivityRecorder: PartnerActivityRecorder,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM partner_audit_log")
        }

        Given("파트너 요청이 발생한다") {
            When("PartnerActivityRecorder.record를 호출하면") {
                partnerActivityRecorder.record(
                    partnerId = 777L,
                    userId = 10L,
                    httpMethod = "POST",
                    requestPath = "/api/goods-seller/products",
                    statusCode = 201,
                    latencyMs = 50,
                    ipAddr = "127.0.0.1",
                    userAgent = "partner-client/1.0",
                    calledAt = ZonedDateTime.now(),
                )

                Then("비동기로 partner_audit_log에 1건 적재된다") {
                    eventually(5.seconds) {
                        val count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM partner_audit_log WHERE partner_id = ?",
                            Long::class.java,
                            777L,
                        )
                        count shouldBe 1L
                    }
                }
            }
        }
    }
}
