package com.sportsapp.domain.mcp.service
import com.sportsapp.domain.mcp.entity.McpTokenStatus
import com.sportsapp.domain.mcp.entity.McpToken
import com.sportsapp.domain.mcp.event.McpAnomalyDetectedEvent
import com.sportsapp.domain.mcp.repository.McpAuditLogCustomRepository
import com.sportsapp.domain.mcp.repository.McpTokenRepository
import com.sportsapp.domain.mcp.dto.DailyCallCount

import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class McpAnomalyDomainServiceTest : BehaviorSpec({

    fun makeToken(
        id: Long,
        userId: Long = 1L,
        createdDaysAgo: Long = 30L,
    ): McpToken = McpToken(
        userId = userId,
        name = "test-token",
        initialTokenHash = "hash-$id",
        initialStatus = McpTokenStatus.ACTIVE,
        initialExpiresAt = null,
    ).also { token ->
        val idField = McpToken::class.java.getDeclaredField("id")
            .apply { isAccessible = true }
        idField.set(token, id)
        val createdAtField = McpToken::class.java.superclass.getDeclaredField("createdAt")
            .apply { isAccessible = true }
        createdAtField.set(token, ZonedDateTime.now().minusDays(createdDaysAgo))
    }

    fun makeService(
        tokenRepo: McpTokenRepository,
        auditRepo: McpAuditLogCustomRepository,
        eventPublisher: DomainEventPublisher,
    ) = McpAnomalyDomainService(
        mcpTokenRepository = tokenRepo,
        mcpAuditLogCustomRepository = auditRepo,
        domainEventPublisher = eventPublisher,
    )

    Given("[U-11] cold-start 토큰은 탐지를 건너뛴다") {
        val tokenRepo = mockk<McpTokenRepository>(relaxed = true)
        val auditRepo = mockk<McpAuditLogCustomRepository>(relaxed = true)
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = makeService(tokenRepo, auditRepo, eventPublisher)

        val token = makeToken(id = 1L, userId = 10L, createdDaysAgo = 5L)
        every { tokenRepo.findById(1L) } returns token

        When("detectForToken을 호출하면") {
            service.detectForToken(tokenId = 1L)

            Then("[U-11] auditLog 집계 쿼리가 호출되지 않는다") {
                verify(exactly = 0) {
                    auditRepo.findDailyCallCountsForBaseline(any(), any(), any())
                }
            }

            Then("[U-11] 이벤트가 발행되지 않는다") {
                verify(exactly = 0) { eventPublisher.publish(any()) }
            }
        }
    }

    Given("[U-12] 정상 패턴 토큰은 이벤트를 발행하지 않는다") {
        val tokenRepo = mockk<McpTokenRepository>(relaxed = true)
        val auditRepo = mockk<McpAuditLogCustomRepository>(relaxed = true)
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = makeService(tokenRepo, auditRepo, eventPublisher)

        val token = makeToken(id = 2L, userId = 20L, createdDaysAgo = 30L)
        every { tokenRepo.findById(2L) } returns token
        // 실제 쿼리는 dayOfMonth 단위 row 7개 반환 (일별 집계)
        every {
            auditRepo.findDailyCallCountsForBaseline(tokenId = 2L, from = any(), to = any())
        } returns listOf(
            DailyCallCount(dayOfMonth = 1, callCount = 5L),
            DailyCallCount(dayOfMonth = 2, callCount = 6L),
            DailyCallCount(dayOfMonth = 3, callCount = 4L),
            DailyCallCount(dayOfMonth = 4, callCount = 5L),
            DailyCallCount(dayOfMonth = 5, callCount = 5L),
            DailyCallCount(dayOfMonth = 6, callCount = 5L),
            DailyCallCount(dayOfMonth = 7, callCount = 5L),
        )
        every {
            auditRepo.findCurrentHourCallCount(tokenId = 2L, from = any())
        } returns 8L

        When("detectForToken을 호출하면 (현재 8, 일평균 5.0 → 비율 1.6)") {
            service.detectForToken(tokenId = 2L)

            Then("[U-12] 이벤트가 발행되지 않는다") {
                verify(exactly = 0) { eventPublisher.publish(any()) }
            }
        }
    }

    Given("[U-13] 비정상 급증 토큰은 McpAnomalyDetectedEvent를 발행한다") {
        val tokenRepo = mockk<McpTokenRepository>(relaxed = true)
        val auditRepo = mockk<McpAuditLogCustomRepository>(relaxed = true)
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = makeService(tokenRepo, auditRepo, eventPublisher)

        val token = makeToken(id = 3L, userId = 30L, createdDaysAgo = 30L)
        every { tokenRepo.findById(3L) } returns token
        // 실제 쿼리는 dayOfMonth 단위 row 7개 반환 (일별 집계)
        every {
            auditRepo.findDailyCallCountsForBaseline(tokenId = 3L, from = any(), to = any())
        } returns listOf(
            DailyCallCount(dayOfMonth = 1, callCount = 10L),
            DailyCallCount(dayOfMonth = 2, callCount = 10L),
            DailyCallCount(dayOfMonth = 3, callCount = 10L),
            DailyCallCount(dayOfMonth = 4, callCount = 10L),
            DailyCallCount(dayOfMonth = 5, callCount = 10L),
            DailyCallCount(dayOfMonth = 6, callCount = 10L),
            DailyCallCount(dayOfMonth = 7, callCount = 10L),
        )
        every {
            auditRepo.findCurrentHourCallCount(tokenId = 3L, from = any())
        } returns 25L

        When("detectForToken을 호출하면 (현재 25, 일평균 10.0 → 비율 2.5)") {
            val publishedEvents = mutableListOf<McpAnomalyDetectedEvent>()
            every { eventPublisher.publish(any()) } answers {
                val event = firstArg<Any>()
                if (event is McpAnomalyDetectedEvent) publishedEvents.add(event)
            }

            service.detectForToken(tokenId = 3L)

            Then("[U-13] McpAnomalyDetectedEvent가 1회 발행된다") {
                verify(exactly = 1) { eventPublisher.publish(any()) }
                publishedEvents shouldHaveSize 1
                publishedEvents[0].tokenId shouldBe 3L
                publishedEvents[0].userId shouldBe 30L
                publishedEvents[0].currentHourCount shouldBe 25L
            }
        }
    }

    Given("[U-14] 토큰이 존재하지 않으면 탐지를 건너뛴다") {
        val tokenRepo = mockk<McpTokenRepository>(relaxed = true)
        val auditRepo = mockk<McpAuditLogCustomRepository>(relaxed = true)
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = makeService(tokenRepo, auditRepo, eventPublisher)

        every { tokenRepo.findById(999L) } returns null

        When("detectForToken을 호출하면") {
            service.detectForToken(tokenId = 999L)

            Then("[U-14] 집계 쿼리 및 이벤트 발행 없이 종료된다") {
                verify(exactly = 0) {
                    auditRepo.findDailyCallCountsForBaseline(any(), any(), any())
                }
                verify(exactly = 0) { eventPublisher.publish(any()) }
            }
        }
    }

    Given("[U-15] detectAll은 모든 ACTIVE 토큰에 대해 탐지를 수행한다") {
        val tokenRepo = mockk<McpTokenRepository>(relaxed = true)
        val auditRepo = mockk<McpAuditLogCustomRepository>(relaxed = true)
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = makeService(tokenRepo, auditRepo, eventPublisher)

        val activeTokens = listOf(
            makeToken(id = 10L, userId = 100L, createdDaysAgo = 5L),
            makeToken(id = 11L, userId = 110L, createdDaysAgo = 5L),
            makeToken(id = 12L, userId = 120L, createdDaysAgo = 5L),
        )
        every { tokenRepo.findAllActive() } returns activeTokens

        When("detectAll을 호출하면") {
            service.detectAll()

            Then("[U-15] findAllActive가 1회 호출되고 개별 findById는 호출되지 않는다") {
                verify(exactly = 1) { tokenRepo.findAllActive() }
                verify(exactly = 0) { tokenRepo.findById(any()) }
            }
        }
    }
})
