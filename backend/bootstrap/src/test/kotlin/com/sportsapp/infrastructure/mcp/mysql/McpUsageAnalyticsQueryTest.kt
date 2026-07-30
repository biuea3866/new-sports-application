package com.sportsapp.infrastructure.mcp.mysql

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.repository.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.repository.McpAuditLogRepository
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.shouldBeBetween
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class McpUsageAnalyticsQueryTest(
    @Autowired private val auditLogRepository: McpAuditLogRepository,
    @Autowired private val auditLogCustomRepository: McpAuditLogCustomRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private val userId = 100L
    private val now = ZonedDateTime.now()
    private val from = now.minusDays(90)
    private val to = now.plusSeconds(1)

    private fun saveLog(
        tokenId: Long?,
        toolName: String = "getBookings",
        statusCode: Int = 200,
        latencyMs: Int = 100,
        calledAt: ZonedDateTime = now.minusDays(1),
        logUserId: Long = userId,
    ): McpAuditLog = auditLogRepository.save(
        McpAuditLog(
            tokenId = tokenId,
            userId = logUserId,
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

        Given("[R-01] userId=100, tool A 3건 + tool B 1건, 기간 내") {
            saveLog(tokenId = 1L, toolName = "getBookings", calledAt = now.minusDays(1))
            saveLog(tokenId = 1L, toolName = "getBookings", calledAt = now.minusDays(2))
            saveLog(tokenId = 1L, toolName = "getBookings", calledAt = now.minusDays(3))
            saveLog(tokenId = 2L, toolName = "createSlot", calledAt = now.minusDays(1))
            // 다른 userId — 집계에 포함되면 안 됨
            saveLog(tokenId = 3L, toolName = "getBookings", calledAt = now.minusDays(1), logUserId = 999L)

            When("findDailyUsageStats 호출") {
                val stats = auditLogCustomRepository.findDailyUsageStats(userId, from, to)

                Then("[R-01] userId=100 의 날짜별·tool별 집계만 반환된다") {
                    stats.isNotEmpty() shouldBe true
                    stats.all { it.callCount > 0 } shouldBe true
                    // getBookings 3건은 3개 다른 날짜이므로 각 1건
                    val getBookingsStats = stats.filter { it.toolName == "getBookings" }
                    getBookingsStats.sumOf { it.callCount } shouldBe 3L
                    val createSlotStats = stats.filter { it.toolName == "createSlot" }
                    createSlotStats.sumOf { it.callCount } shouldBe 1L
                }
            }
        }

        Given("[R-02] userId=100, getBookings 5건 + createSlot 2건 + checkFacility 1건") {
            repeat(5) { saveLog(tokenId = 1L, toolName = "getBookings") }
            repeat(2) { saveLog(tokenId = 1L, toolName = "createSlot") }
            saveLog(tokenId = 1L, toolName = "checkFacility")

            When("findToolCallStats 호출") {
                val stats = auditLogCustomRepository.findToolCallStats(userId, from, to)

                Then("[R-02] callCount 내림차순으로 tool별 집계가 반환된다") {
                    stats shouldHaveSize 3
                    stats[0].toolName shouldBe "getBookings"
                    stats[0].callCount shouldBe 5L
                    stats[1].toolName shouldBe "createSlot"
                    stats[1].callCount shouldBe 2L
                    stats[2].toolName shouldBe "checkFacility"
                    stats[2].callCount shouldBe 1L
                }
            }
        }

        Given("[R-03] userId=100, 총 10건 중 에러(statusCode>=400) 3건") {
            repeat(7) { saveLog(tokenId = 1L, statusCode = 200) }
            repeat(2) { saveLog(tokenId = 1L, statusCode = 403) }
            saveLog(tokenId = 1L, statusCode = 429)

            When("findErrorRateStat 호출") {
                val stat = auditLogCustomRepository.findErrorRateStat(userId, from, to)

                Then("[R-03] totalCount=10, errorCount=3, errorRatePercent=30%") {
                    stat.totalCount shouldBe 10L
                    stat.errorCount shouldBe 3L
                    stat.errorRatePercent.shouldBeBetween(29.9, 30.1, 0.0)
                }
            }
        }

        Given("[R-04] userId=100, getBookings latency [100..1000ms] 10건") {
            val latencies = listOf(100, 200, 300, 400, 500, 600, 700, 800, 900, 1000)
            latencies.forEach { latencyMs ->
                saveLog(tokenId = 1L, toolName = "getBookings", latencyMs = latencyMs)
            }

            When("findLatencyMsByTool 호출 후 P95 산출") {
                val latencyByTool = auditLogCustomRepository.findLatencyMsByTool(userId, from, to)

                Then("[R-04] getBookings latency 10건이 조회되고 정렬 기반 P95 인덱스(8번째)=900ms") {
                    latencyByTool.containsKey("getBookings") shouldBe true
                    val toolLatencies = requireNotNull(latencyByTool["getBookings"])
                    toolLatencies shouldHaveSize 10
                    val sorted = toolLatencies.sorted()
                    // P95 인덱스: ((N-1) * 0.95).toInt() = ((10-1) * 0.95).toInt() = 8 → sorted[8] = 900
                    val p95Index = ((sorted.size - 1) * 0.95).toInt()
                    sorted[p95Index] shouldBe 900
                }
            }
        }

        Given("[R-05] userId=100, token 1은 5건·token 2는 3건·token 3은 1건, limit=2") {
            repeat(5) { saveLog(tokenId = 1L) }
            repeat(3) { saveLog(tokenId = 2L) }
            saveLog(tokenId = 3L)

            When("findTokenUsageStats(limit=2) 호출") {
                val stats = auditLogCustomRepository.findTokenUsageStats(userId, from, to, limit = 2)

                Then("[R-05] callCount 내림차순 TOP 2만 반환된다") {
                    stats shouldHaveSize 2
                    stats[0].tokenId shouldBe 1L
                    stats[0].callCount shouldBe 5L
                    stats[1].tokenId shouldBe 2L
                    stats[1].callCount shouldBe 3L
                }
            }
        }

        Given("[R-06] userId=100, token 1 에러 2건 포함 5건") {
            repeat(3) { saveLog(tokenId = 1L, statusCode = 200) }
            repeat(2) { saveLog(tokenId = 1L, statusCode = 403) }

            When("findTokenUsageStats 호출") {
                val stats = auditLogCustomRepository.findTokenUsageStats(userId, from, to, limit = 20)

                Then("[R-06] token 1의 callCount=5, errorCount=2, lastCalledAt 존재") {
                    stats shouldHaveSize 1
                    stats[0].callCount shouldBe 5L
                    stats[0].errorCount shouldBe 2L
                    stats[0].lastCalledAt shouldNotBe null
                }
            }
        }

        Given("[R-07] 데이터가 없는 userId로 조회") {
            When("findErrorRateStat 호출") {
                val stat = auditLogCustomRepository.findErrorRateStat(userId = 99999L, from = from, to = to)

                Then("[R-07] totalCount=0, errorCount=0, errorRatePercent=0.0") {
                    stat.totalCount shouldBe 0L
                    stat.errorCount shouldBe 0L
                    stat.errorRatePercent shouldBe 0.0
                }
            }
        }
    }
}
