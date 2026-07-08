package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.RecordToolInvocationCommand

import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.service.McpAuditLogDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.ZonedDateTime

class RecordToolInvocationUseCaseTest : BehaviorSpec({

    val mcpAuditLogDomainService = mockk<McpAuditLogDomainService>(relaxed = true)
    val useCase = RecordToolInvocationUseCase(mcpAuditLogDomainService)

    Given("[U-01] 정상 command가 주어지면") {
        val calledAt = ZonedDateTime.now()
        val command = RecordToolInvocationCommand(
            tokenId = 42L,
            userId = 100L,
            toolName = "getFacilities",
            paramsMasked = """{"gu":"강남구"}""",
            statusCode = 200,
            latencyMs = 15,
            ipAddr = "1.2.3.4",
            clientUserAgent = "TestAgent/1.0",
            calledAt = calledAt,
        )
        val capturedLog = slot<McpAuditLog>()
        every { mcpAuditLogDomainService.recordToolInvocation(capture(capturedLog)) } returns Unit

        When("execute를 호출하면") {
            useCase.execute(command)

            Then("[U-01] McpAuditLog가 올바른 필드 값으로 DomainService에 전달된다") {
                verify(exactly = 1) { mcpAuditLogDomainService.recordToolInvocation(any()) }
                capturedLog.captured.tokenId shouldBe 42L
                capturedLog.captured.userId shouldBe 100L
                capturedLog.captured.toolName shouldBe "getFacilities"
                capturedLog.captured.paramsMasked shouldBe """{"gu":"강남구"}"""
                capturedLog.captured.statusCode shouldBe 200
                capturedLog.captured.latencyMs shouldBe 15
                capturedLog.captured.ipAddr shouldBe "1.2.3.4"
                capturedLog.captured.clientUserAgent shouldBe "TestAgent/1.0"
                capturedLog.captured.calledAt shouldBe calledAt
            }
        }
    }

    Given("[U-02] tokenId가 null인 command가 주어지면 (비인증 호출)") {
        val command = RecordToolInvocationCommand(
            tokenId = null,
            userId = 0L,
            toolName = "getBookings",
            paramsMasked = "{}",
            statusCode = 500,
            latencyMs = 5,
            ipAddr = null,
            clientUserAgent = null,
            calledAt = ZonedDateTime.now(),
        )
        val capturedLog = slot<McpAuditLog>()
        every { mcpAuditLogDomainService.recordToolInvocation(capture(capturedLog)) } returns Unit

        When("execute를 호출하면") {
            useCase.execute(command)

            Then("[U-02] tokenId=null, statusCode=500으로 DomainService에 전달된다") {
                capturedLog.captured.tokenId shouldBe null
                capturedLog.captured.statusCode shouldBe 500
                capturedLog.captured.ipAddr shouldBe null
            }
        }
    }
})
