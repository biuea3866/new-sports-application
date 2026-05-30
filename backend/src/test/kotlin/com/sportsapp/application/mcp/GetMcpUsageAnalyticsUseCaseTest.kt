package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.DailyUsageStat
import com.sportsapp.domain.mcp.ErrorRateStat
import com.sportsapp.domain.mcp.McpAuditLogDomainService
import com.sportsapp.domain.mcp.McpUsageAnalyticsResult
import com.sportsapp.domain.mcp.ToolCallStat
import com.sportsapp.domain.mcp.ToolLatencyStat
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class GetMcpUsageAnalyticsUseCaseTest : BehaviorSpec({

    val mcpAuditLogDomainService = mockk<McpAuditLogDomainService>()
    val useCase = GetMcpUsageAnalyticsUseCase(mcpAuditLogDomainService)

    val now = ZonedDateTime.now()
    val from = now.minusDays(30)
    val to = now

    Given("[U-01] userId=1 의 집계 데이터가 있을 때") {
        val command = GetMcpUsageAnalyticsCommand(userId = 1L, from = from, to = to)

        val expectedResult = McpUsageAnalyticsResult(
            dailyStats = listOf(
                DailyUsageStat(date = "2026-05-29", toolName = "getBookings", callCount = 5L),
            ),
            toolCallStats = listOf(
                ToolCallStat(toolName = "getBookings", callCount = 10L),
                ToolCallStat(toolName = "createSlot", callCount = 3L),
            ),
            errorRateStat = ErrorRateStat(totalCount = 13L, errorCount = 1L),
            toolLatencyStats = listOf(
                ToolLatencyStat(toolName = "getBookings", p95LatencyMs = 320),
            ),
            tokenUsageStats = emptyList(),
        )

        every { mcpAuditLogDomainService.aggregateUsageAnalytics(1L, from, to) } returns expectedResult

        When("execute 호출") {
            val response = useCase.execute(command)

            Then("[U-01] DomainService 집계 결과가 Response로 변환되어 반환된다") {
                response.dailyStats.size shouldBe 1
                response.dailyStats[0].toolName shouldBe "getBookings"
                response.dailyStats[0].callCount shouldBe 5L
                response.toolCallStats.size shouldBe 2
                response.toolCallStats[0].toolName shouldBe "getBookings"
                response.errorRateStat.totalCount shouldBe 13L
                response.errorRateStat.errorCount shouldBe 1L
                response.toolLatencyStats.size shouldBe 1
                response.toolLatencyStats[0].p95LatencyMs shouldBe 320
            }
        }
    }

    Given("[U-02] 기간 내 데이터가 없는 userId로 조회") {
        val command = GetMcpUsageAnalyticsCommand(userId = 999L, from = from, to = to)

        val emptyResult = McpUsageAnalyticsResult(
            dailyStats = emptyList(),
            toolCallStats = emptyList(),
            errorRateStat = ErrorRateStat(totalCount = 0L, errorCount = 0L),
            toolLatencyStats = emptyList(),
            tokenUsageStats = emptyList(),
        )

        every { mcpAuditLogDomainService.aggregateUsageAnalytics(999L, from, to) } returns emptyResult

        When("execute 호출") {
            val response = useCase.execute(command)

            Then("[U-02] 모든 집계 항목이 빈 상태로 반환된다") {
                response.dailyStats.isEmpty() shouldBe true
                response.toolCallStats.isEmpty() shouldBe true
                response.errorRateStat.totalCount shouldBe 0L
                response.errorRateStat.errorRatePercent shouldBe 0.0
                response.toolLatencyStats.isEmpty() shouldBe true
                response.tokenUsageStats.isEmpty() shouldBe true
            }
        }
    }

    Given("[U-03] command의 userId가 DomainService에 그대로 전달되는지 확인") {
        val command = GetMcpUsageAnalyticsCommand(userId = 42L, from = from, to = to)

        every { mcpAuditLogDomainService.aggregateUsageAnalytics(42L, from, to) } returns McpUsageAnalyticsResult(
            dailyStats = emptyList(),
            toolCallStats = emptyList(),
            errorRateStat = ErrorRateStat(totalCount = 0L, errorCount = 0L),
            toolLatencyStats = emptyList(),
            tokenUsageStats = emptyList(),
        )

        When("execute 호출") {
            useCase.execute(command)

            Then("[U-03] aggregateUsageAnalytics가 userId=42, 정확한 기간으로 1회 호출된다") {
                verify(exactly = 1) { mcpAuditLogDomainService.aggregateUsageAnalytics(42L, from, to) }
            }
        }
    }
})
