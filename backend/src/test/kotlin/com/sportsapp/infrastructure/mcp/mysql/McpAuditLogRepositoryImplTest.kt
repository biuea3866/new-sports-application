package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.repository.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.repository.McpAuditLogRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class McpAuditLogRepositoryImplTest(
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val auditLogCustomRepository: McpAuditLogCustomRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun createLog(
        tokenId: Long?,
        userId: Long,
        toolName: String = "getBookings",
        statusCode: Int = 200,
        latencyMs: Int = 100,
        calledAt: ZonedDateTime = ZonedDateTime.now(),
    ): McpAuditLog = auditLogRepository.save(
        McpAuditLog(
            tokenId = tokenId,
            userId = userId,
            toolName = toolName,
            paramsMasked = null,
            statusCode = statusCode,
            latencyMs = latencyMs,
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

        Given("[R-01] userId=10 3 logs within 24h window") {
            val now = ZonedDateTime.now()
            val from = now.minusHours(24)
            val to = now.plusSeconds(1)

            createLog(tokenId = 1L, userId = 10L, calledAt = now.minusHours(1))
            createLog(tokenId = 1L, userId = 10L, calledAt = now.minusHours(2))
            createLog(tokenId = 1L, userId = 10L, calledAt = now.minusHours(3))
            createLog(tokenId = 2L, userId = 99L, calledAt = now.minusHours(1))

            When("query by userId=10 with DESC sort paging") {
                val pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "calledAt"))
                val page = auditLogRepository.findByUserIdAndCalledAtBetween(10L, from, to, pageable)

                Then("[R-01] 3 rows returned in calledAt DESC order") {
                    page.totalElements shouldBe 3L
                    page.content shouldHaveSize 3
                    val calledAts = page.content.map { it.calledAt }
                    calledAts shouldBe calledAts.sortedDescending()
                }
            }
        }

        Given("[R-02] audit log with tokenId=null") {
            createLog(tokenId = null, userId = 20L)

            When("query by userId=20") {
                val now = ZonedDateTime.now()
                val page = auditLogRepository.findByUserIdAndCalledAtBetween(
                    userId = 20L,
                    from = now.minusMinutes(1),
                    to = now.plusMinutes(1),
                    pageable = PageRequest.of(0, 10),
                )

                Then("[R-02] row with tokenId=null is found (FK NULL allowed)") {
                    page.content shouldHaveSize 1
                    page.content[0].tokenId.shouldBeNull()
                    page.content[0].userId shouldBe 20L
                }
            }
        }

        Given("[R-03] tokenId=5 has 3 calls within 24h, tokenId=6 has 1 call") {
            val now = ZonedDateTime.now()
            val windowStart = now.minusHours(24)

            createLog(tokenId = 5L, userId = 30L, calledAt = now.minusHours(1))
            createLog(tokenId = 5L, userId = 30L, calledAt = now.minusHours(2))
            createLog(tokenId = 5L, userId = 30L, calledAt = now.minusHours(3))
            createLog(tokenId = 6L, userId = 31L, calledAt = now.minusHours(1))
            createLog(tokenId = 5L, userId = 30L, calledAt = now.minusHours(25))

            When("aggregate query with tokenIds=[5,6] from windowStart") {
                val stats = auditLogCustomRepository.findCallStatsByTokenIdIn(
                    tokenIds = listOf(5L, 6L),
                    from = windowStart,
                )

                Then("[R-03] tokenId=5 has count=3, tokenId=6 has count=1") {
                    stats shouldHaveSize 2
                    val statsMap = stats.associate { it.tokenId to it.callCount }
                    statsMap[5L] shouldBe 3L
                    statsMap[6L] shouldBe 1L
                }
            }
        }

        Given("[R-04] rows with various called_at values") {
            val now = ZonedDateTime.now()
            createLog(tokenId = 7L, userId = 40L, calledAt = now.minusDays(100))
            createLog(tokenId = 7L, userId = 40L, calledAt = now.minusDays(10))

            When("EXPLAIN SELECT for rows older than 90 days") {
                val ninetyDaysAgo = now.minusDays(90)
                val cutoff = ninetyDaysAgo.toInstant().toString()
                    .replace("T", " ").replace("Z", "")
                val explainResult = jdbcTemplate.queryForList(
                    "EXPLAIN SELECT id FROM mcp_audit_logs WHERE called_at < '$cutoff'"
                )

                Then("[R-04] EXPLAIN uses called_at index (range or ref type)") {
                    explainResult shouldHaveSize 1
                    val row = explainResult[0]
                    val possibleKeys = row["possible_keys"]?.toString() ?: ""
                    val key = row["key"]?.toString() ?: ""
                    val typeValue = row["type"]?.toString() ?: ""

                    (possibleKeys.contains("idx_mcp_audit_logs_called_at") ||
                        key.contains("idx_mcp_audit_logs_called_at") ||
                        typeValue == "range" || typeValue == "ref") shouldBe true
                }
            }
        }
    }
}
