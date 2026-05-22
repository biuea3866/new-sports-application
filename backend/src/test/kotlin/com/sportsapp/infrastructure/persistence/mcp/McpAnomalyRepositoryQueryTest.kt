package com.sportsapp.infrastructure.persistence.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.McpAuditLogRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class McpAnomalyRepositoryQueryTest(
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val auditLogCustomRepository: McpAuditLogCustomRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun saveLog(
        tokenId: Long,
        calledAt: ZonedDateTime,
        statusCode: Int = 200,
    ): McpAuditLog = auditLogRepository.save(
        McpAuditLog(
            tokenId = tokenId,
            userId = 100L,
            toolName = "getBookings",
            paramsMasked = null,
            statusCode = statusCode,
            latencyMs = 100,
            clientUserAgent = null,
            ipAddr = null,
            asn = null,
            calledAt = calledAt,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
        }

        Given("[R-05] 7일 내 tokenId=10 의 시간대별 집계") {
            val now = ZonedDateTime.now()
            val from = now.minusDays(7)
            val to = now

            saveLog(tokenId = 10L, calledAt = now.minusDays(1).withHour(10))
            saveLog(tokenId = 10L, calledAt = now.minusDays(2).withHour(10))
            saveLog(tokenId = 10L, calledAt = now.minusDays(3).withHour(14))
            saveLog(tokenId = 10L, calledAt = now.minusDays(8).withHour(10))
            saveLog(tokenId = 99L, calledAt = now.minusDays(1).withHour(10))

            When("findHourlyCallCountsForBaseline(tokenId=10, from=7일전, to=현재)를 호출하면") {
                val result = auditLogCustomRepository.findHourlyCallCountsForBaseline(
                    tokenId = 10L,
                    from = from,
                    to = to,
                )

                Then("[R-05] 7일 범위 내 tokenId=10 레코드만 집계된다 (8일 전 및 tokenId=99 제외)") {
                    val totalCount = result.sumOf { it.callCount }
                    totalCount shouldBe 3L
                }
            }
        }

        Given("[R-06] 현재 1시간 이내 tokenId=20 호출 수") {
            val now = ZonedDateTime.now()

            saveLog(tokenId = 20L, calledAt = now.minusMinutes(30))
            saveLog(tokenId = 20L, calledAt = now.minusMinutes(45))
            saveLog(tokenId = 20L, calledAt = now.minusMinutes(90))
            saveLog(tokenId = 99L, calledAt = now.minusMinutes(10))

            When("findCurrentHourCallCount(tokenId=20, from=1시간전)를 호출하면") {
                val currentHourFrom = now.minusHours(1)
                val count = auditLogCustomRepository.findCurrentHourCallCount(
                    tokenId = 20L,
                    from = currentHourFrom,
                )

                Then("[R-06] 1시간 이내 tokenId=20 호출 수 2개가 반환된다") {
                    count shouldBe 2L
                }
            }
        }

        Given("[R-07] 호출 로그가 없는 토큰의 현재 1시간 호출 수") {
            When("findCurrentHourCallCount(tokenId=999, from=1시간전)를 호출하면") {
                val count = auditLogCustomRepository.findCurrentHourCallCount(
                    tokenId = 999L,
                    from = ZonedDateTime.now().minusHours(1),
                )

                Then("[R-07] 0이 반환된다") {
                    count shouldBe 0L
                }
            }
        }

        Given("[R-08] 7일 베이스라인 집계 — tokenId=30 로그 없음") {
            val now = ZonedDateTime.now()

            When("findHourlyCallCountsForBaseline(tokenId=30)를 호출하면") {
                val result = auditLogCustomRepository.findHourlyCallCountsForBaseline(
                    tokenId = 30L,
                    from = now.minusDays(7),
                    to = now,
                )

                Then("[R-08] 빈 리스트가 반환된다") {
                    result shouldBe emptyList()
                }
            }
        }
    }
}
