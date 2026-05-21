package com.sportsapp.domain.mcp

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import java.time.ZonedDateTime

class McpAuditLogDomainServiceTest : BehaviorSpec({

    val mcpAuditLogRepository = mockk<McpAuditLogRepository>()
    val service = McpAuditLogDomainService(mcpAuditLogRepository)

    fun makeAuditLog(id: Long, userId: Long, calledAt: ZonedDateTime): McpAuditLog {
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
            calledAt = calledAt,
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

    Given("[U-04] userId + 기간으로 listByUser를 호출하면") {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "calledAt"))
        val logs = listOf(
            makeAuditLog(2L, 1L, now.minusHours(1)),
            makeAuditLog(1L, 1L, now.minusDays(1)),
        )
        val page = PageImpl(logs, pageable, 2)
        every { mcpAuditLogRepository.findByUserIdAndCalledAtBetween(1L, from, to, pageable) } returns page

        When("repository.findByUserIdAndCalledAtBetween를 위임 호출하면") {
            val result = service.listByUser(userId = 1L, from = from, to = to, pageable = pageable)

            Then("[U-04] Repository 결과가 그대로 반환된다") {
                result.totalElements shouldBe 2
                result.content.size shouldBe 2
            }

            Then("[U-04] repository가 정확히 1회 호출된다") {
                verify(exactly = 1) {
                    mcpAuditLogRepository.findByUserIdAndCalledAtBetween(1L, from, to, pageable)
                }
            }
        }
    }

    Given("[U-05] 기간 내 로그가 없는 userId로 listByUser를 호출하면") {
        val pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "calledAt"))
        every { mcpAuditLogRepository.findByUserIdAndCalledAtBetween(99L, from, to, pageable) } returns
            PageImpl(emptyList(), pageable, 0)

        When("repository.findByUserIdAndCalledAtBetween를 호출하면") {
            val result = service.listByUser(userId = 99L, from = from, to = to, pageable = pageable)

            Then("[U-05] 빈 페이지가 반환된다") {
                result.isEmpty shouldBe true
                result.totalElements shouldBe 0
            }
        }
    }
})
