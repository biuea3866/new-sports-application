package com.sportsapp.application.mcp

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.mcp.McpAnomalyEvent
import com.sportsapp.domain.mcp.McpAnomalyEventDomainService
import com.sportsapp.domain.mcp.McpAnomalyEventStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class MarkAnomalyFalsePositiveUseCaseTest : BehaviorSpec({

    val mcpAnomalyEventDomainService = mockk<McpAnomalyEventDomainService>()
    val useCase = MarkAnomalyFalsePositiveUseCase(mcpAnomalyEventDomainService)

    fun createOpenAnomalyEvent(ownerUserId: Long): McpAnomalyEvent = McpAnomalyEvent(
        sourceEventId = "evt-test-${ownerUserId}",
        tokenId = 1L,
        ownerUserId = ownerUserId,
        detectedAt = ZonedDateTime.now(),
        currentHourCount = 200L,
        baselineAverage = 50.0,
    )

    Given("[U-01] OPEN 상태의 McpAnomalyEvent (ownerUserId=10)") {
        val anomalyEvent = createOpenAnomalyEvent(ownerUserId = 10L)

        every { mcpAnomalyEventDomainService.getByIdAndOwnerUserId(1L, 10L) } returns anomalyEvent
        every { mcpAnomalyEventDomainService.persist(anomalyEvent) } returns anomalyEvent

        When("ownerUserId=10인 사용자가 false positive 마킹하면") {
            val command = MarkAnomalyFalsePositiveCommand(
                anomalyEventId = 1L,
                requestUserId = 10L,
                note = "정상 트래픽",
            )
            val response = useCase.execute(command)

            Then("[U-01] status=FALSE_POSITIVE로 변경되고 응답이 반환된다") {
                response.status shouldBe McpAnomalyEventStatus.FALSE_POSITIVE
                response.falsePositive shouldBe true
                response.note shouldBe "정상 트래픽"
            }
        }
    }

    Given("[U-02] 다른 사용자(userId=99)가 id=1 이벤트에 접근 시도") {
        every {
            mcpAnomalyEventDomainService.getByIdAndOwnerUserId(1L, 99L)
        } throws ResourceNotFoundException("McpAnomalyEvent", 1L)

        When("userId=99로 false positive 마킹 시도하면") {
            val command = MarkAnomalyFalsePositiveCommand(
                anomalyEventId = 1L,
                requestUserId = 99L,
                note = null,
            )

            Then("[U-02] ResourceNotFoundException이 발생한다 (IDOR 차단 — DB 레벨 필터)") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(command)
                }
            }
        }
    }
})
