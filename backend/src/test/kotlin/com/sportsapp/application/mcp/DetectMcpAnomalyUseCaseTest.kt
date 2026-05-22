package com.sportsapp.application.mcp

import com.sportsapp.domain.mcp.McpAnomalyDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify

class DetectMcpAnomalyUseCaseTest : BehaviorSpec({

    val mcpAnomalyDomainService = mockk<McpAnomalyDomainService>(relaxed = true)
    val useCase = DetectMcpAnomalyUseCase(mcpAnomalyDomainService)

    Given("[U-16] execute 호출 시") {
        When("DetectMcpAnomalyUseCase.execute를 호출하면") {
            useCase.execute()

            Then("[U-16] McpAnomalyDomainService.detectAll이 1회 호출된다") {
                verify(exactly = 1) { mcpAnomalyDomainService.detectAll() }
            }
        }
    }
})
