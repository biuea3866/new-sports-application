package com.sportsapp.application.mcp.usecase
import com.sportsapp.application.mcp.dto.PersistAnomalyEventCommand

import com.sportsapp.domain.mcp.entity.McpAnomalyEvent
import com.sportsapp.domain.mcp.service.McpAnomalyEventDomainService
import com.sportsapp.domain.mcp.entity.McpAnomalyEventStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import java.time.ZonedDateTime

class PersistAnomalyEventUseCaseTest : BehaviorSpec({

    val mcpAnomalyEventDomainService = mockk<McpAnomalyEventDomainService>()
    val useCase = PersistAnomalyEventUseCase(mcpAnomalyEventDomainService)

    fun buildCommand(sourceEventId: String = "evt-001"): PersistAnomalyEventCommand = PersistAnomalyEventCommand(
        sourceEventId = sourceEventId,
        tokenId = 1L,
        ownerUserId = 10L,
        detectedAt = ZonedDateTime.now(),
        currentHourCount = 200L,
        baselineAverage = 50.0,
    )

    Given("[U-01] 중복 아닌 sourceEventId 로 execute()를 호출하면") {
        val command = buildCommand("evt-001")
        val capturedSlot = slot<McpAnomalyEvent>()
        every { mcpAnomalyEventDomainService.persistIfNotDuplicate(capture(capturedSlot)) } answers {
            capturedSlot.captured
        }

        When("execute()를 호출하면") {
            val response = useCase.execute(command)

            Then("[U-01] OPEN 상태 응답이 반환된다") {
                response?.tokenId shouldBe 1L
                response?.ownerUserId shouldBe 10L
                response?.currentHourCount shouldBe 200L
                response?.baselineAverage shouldBe 50.0
                response?.status shouldBe McpAnomalyEventStatus.OPEN
                response?.falsePositive shouldBe false
            }
        }
    }

    Given("[U-02] 동일 sourceEventId 로 DomainService가 null 반환하는 경우 (멱등 — 이미 존재)") {
        val command = buildCommand("evt-dup")
        every { mcpAnomalyEventDomainService.persistIfNotDuplicate(any()) } returns null

        When("execute()를 호출하면") {
            val response = useCase.execute(command)

            Then("[U-02] null 이 반환된다 (중복 skip)") {
                response.shouldBeNull()
            }
        }
    }
})
