package com.sportsapp.domain.mcp.service
import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.repository.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.repository.McpAuditLogRepository

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class McpAuditLogDomainServiceRecordTest : BehaviorSpec({

    val mcpAuditLogRepository = mockk<McpAuditLogRepository>()
    val mcpAuditLogCustomRepository = mockk<McpAuditLogCustomRepository>()
    val service = McpAuditLogDomainService(mcpAuditLogRepository, mcpAuditLogCustomRepository)

    Given("[U-01] 정상 tool 호출 정보가 담긴 McpAuditLog가 주어지면") {
        val calledAt = ZonedDateTime.now()
        val slot = slot<McpAuditLog>()
        every { mcpAuditLogRepository.save(capture(slot)) } answers { slot.captured }

        val auditLog = McpAuditLog(
            tokenId = 10L,
            userId = 200L,
            toolName = "getBookings",
            paramsMasked = """{"arg0":200,"arg1":null}""",
            statusCode = 200,
            latencyMs = 85,
            clientUserAgent = "mcp-client/1.0",
            ipAddr = "10.0.0.1",
            asn = null,
            calledAt = calledAt,
        )

        When("recordToolInvocation을 호출하면") {
            service.recordToolInvocation(auditLog)

            Then("[U-01] 전달된 McpAuditLog가 그대로 save된다") {
                verify(exactly = 1) { mcpAuditLogRepository.save(any()) }
                slot.captured.tokenId shouldBe 10L
                slot.captured.userId shouldBe 200L
                slot.captured.toolName shouldBe "getBookings"
                slot.captured.statusCode shouldBe 200
                slot.captured.latencyMs shouldBe 85
                slot.captured.ipAddr shouldBe "10.0.0.1"
                slot.captured.calledAt shouldBe calledAt
            }
        }
    }

    Given("[U-02] 에러 발생 시 statusCode 500이 담긴 McpAuditLog가 주어지면") {
        val calledAt = ZonedDateTime.now()
        val slot = slot<McpAuditLog>()
        every { mcpAuditLogRepository.save(capture(slot)) } answers { slot.captured }

        val auditLog = McpAuditLog(
            tokenId = null,
            userId = 300L,
            toolName = "getFacilities",
            paramsMasked = null,
            statusCode = 500,
            latencyMs = 1200,
            clientUserAgent = null,
            ipAddr = null,
            asn = null,
            calledAt = calledAt,
        )

        When("recordToolInvocation을 호출하면") {
            service.recordToolInvocation(auditLog)

            Then("[U-02] statusCode 500과 tokenId null이 그대로 저장된다") {
                slot.captured.statusCode shouldBe 500
                slot.captured.tokenId shouldBe null
                slot.captured.latencyMs shouldBe 1200
            }
        }
    }
})
