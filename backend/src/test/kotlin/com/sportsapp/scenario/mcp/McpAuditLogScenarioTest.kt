package com.sportsapp.scenario.mcp

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.McpAuditLog
import com.sportsapp.domain.mcp.McpAuditLogRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class McpAuditLogScenarioTest(
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE mcp_audit_logs")
        }

        Given("[S-01] single tool call recorded") {
            val now = ZonedDateTime.now()
            val userId = 100L
            val tokenId = 50L

            auditLogRepository.save(
                McpAuditLog(
                    tokenId = tokenId,
                    userId = userId,
                    toolName = "getBookings",
                    paramsMasked = null,
                    statusCode = 200,
                    latencyMs = 85,
                    clientUserAgent = "mcp-client/1.0",
                    ipAddr = "10.0.0.1",
                    asn = null,
                    calledAt = now,
                )
            )

            When("findByUserIdAndCalledAtBetween called") {
                val page = auditLogRepository.findByUserIdAndCalledAtBetween(
                    userId = userId,
                    from = now.minusMinutes(1),
                    to = now.plusMinutes(1),
                    pageable = PageRequest.of(0, 10),
                )

                Then("[S-01] 1 row is returned") {
                    page.content shouldHaveSize 1
                    page.content[0].tokenId shouldBe tokenId
                    page.content[0].userId shouldBe userId
                    page.content[0].toolName shouldBe "getBookings"
                    page.content[0].statusCode shouldBe 200
                }
            }
        }

        Given("[S-02] audit log saved with tokenId=60") {
            val now = ZonedDateTime.now()
            val userId = 200L
            val tokenId = 60L

            auditLogRepository.save(
                McpAuditLog(
                    tokenId = tokenId,
                    userId = userId,
                    toolName = "createSlot",
                    paramsMasked = null,
                    statusCode = 201,
                    latencyMs = 130,
                    clientUserAgent = null,
                    ipAddr = null,
                    asn = null,
                    calledAt = now,
                )
            )

            When("mcp_token with same id is soft-deleted") {
                jdbcTemplate.execute(
                    "UPDATE mcp_tokens SET deleted_at = NOW(6), deleted_by = 1, updated_at = NOW(6) " +
                        "WHERE id = $tokenId"
                )

                val page = auditLogRepository.findByUserIdAndCalledAtBetween(
                    userId = userId,
                    from = now.minusMinutes(1),
                    to = now.plusMinutes(1),
                    pageable = PageRequest.of(0, 10),
                )

                Then("[S-02] audit log with tokenId=${tokenId} is preserved (not deleted)") {
                    page.content shouldHaveSize 1
                    page.content[0].tokenId shouldBe tokenId
                }
            }
        }
    }
}
