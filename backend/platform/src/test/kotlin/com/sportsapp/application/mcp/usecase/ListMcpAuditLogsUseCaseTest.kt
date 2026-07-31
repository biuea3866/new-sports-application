package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.ListMcpAuditLogsCommand

import com.sportsapp.domain.mcp.entity.McpAuditLog
import com.sportsapp.domain.mcp.service.McpAuditLogDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZonedDateTime

class ListMcpAuditLogsUseCaseTest : BehaviorSpec({

    val mcpAuditLogDomainService = mockk<McpAuditLogDomainService>()
    val useCase = ListMcpAuditLogsUseCase(mcpAuditLogDomainService)

    fun makeAuditLog(id: Long, userId: Long): McpAuditLog {
        val log = McpAuditLog(
            tokenId = 10L,
            userId = userId,
            toolName = "read:facility",
            paramsMasked = null,
            statusCode = 200,
            latencyMs = 50,
            clientUserAgent = null,
            ipAddr = "127.0.0.1",
            asn = null,
            calledAt = ZonedDateTime.now(),
        )
        val idField = McpAuditLog::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(log, id)
        val createdAtField = McpAuditLog::class.java.getDeclaredField("createdAt")
        createdAtField.isAccessible = true
        createdAtField.set(log, ZonedDateTime.now())
        return log
    }

    val now = ZonedDateTime.now()
    val from = now.minusDays(7)
    val to = now

    val sortByCalledAtDesc = Sort.by(Sort.Direction.DESC, "calledAt")

    Given("[U-01] 해당 기간에 audit log 2건이 있는 userId로 조회하면") {
        val pageable = PageRequest.of(0, 20, sortByCalledAtDesc)
        val command = ListMcpAuditLogsCommand(
            userId = 1L,
            startCalledAt = from,
            endCalledAt = to,
            page = 0,
            size = 20,
        )
        val logs = listOf(makeAuditLog(1L, 1L), makeAuditLog(2L, 1L))
        val page = PageImpl(logs, pageable, 2)
        every { mcpAuditLogDomainService.listByUser(1L, from, to, pageable) } returns page

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-01] 2건의 감사 로그가 포함된 페이지 응답이 반환된다") {
                result.content.size shouldBe 2
                result.totalElements shouldBe 2
                result.content[0].id shouldBe 1L
                result.content[1].id shouldBe 2L
            }
        }
    }

    Given("[U-02] 해당 기간에 audit log가 없는 userId로 조회하면") {
        val pageable = PageRequest.of(0, 20, sortByCalledAtDesc)
        val command = ListMcpAuditLogsCommand(
            userId = 99L,
            startCalledAt = from,
            endCalledAt = to,
            page = 0,
            size = 20,
        )
        every { mcpAuditLogDomainService.listByUser(99L, from, to, pageable) } returns PageImpl(emptyList(), pageable, 0)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-02] 빈 페이지 응답이 반환된다") {
                result.content.isEmpty() shouldBe true
                result.totalElements shouldBe 0
            }
        }
    }

    Given("[U-03] page=1, size=5 커스텀 페이징으로 조회하면") {
        val pageable = PageRequest.of(1, 5, sortByCalledAtDesc)
        val command = ListMcpAuditLogsCommand(
            userId = 1L,
            startCalledAt = from,
            endCalledAt = to,
            page = 1,
            size = 5,
        )
        every { mcpAuditLogDomainService.listByUser(1L, from, to, pageable) } returns PageImpl(emptyList(), pageable, 10)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("[U-03] 전달된 pageable 파라미터가 DomainService에 그대로 전달된다") {
                result.totalElements shouldBe 10
                result.pageNumber shouldBe 1
                result.pageSize shouldBe 5
            }
        }
    }
})
