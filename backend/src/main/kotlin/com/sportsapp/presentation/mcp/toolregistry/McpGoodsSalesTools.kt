package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.goods.GetGoodsSalesUseCase
import com.sportsapp.application.goods.GoodsSalesResponse
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 상품(Goods) 판매 통계 조회.
 * scope: read:goods:sales
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 * IDOR 방지: ownerUserId 를 LLM 파라미터로 받지 않고 SecurityContext 의 McpAuthenticatedPrincipal.userId 사용.
 */
@Component
@Profile("!test-jpa")
class McpGoodsSalesTools(
    private val getGoodsSalesUseCase: GetGoodsSalesUseCase,
) {
    @PreAuthorize("@authz.hasMcpScope('read:goods:sales')")
    @Tool(
        name = "getGoodsSales",
        description = "인증된 B2B 운영자의 상품 판매 통계를 조회합니다. 활성 상품 수·품절 상품 수·확정 주문 수·총 매출액을 반환합니다.",
    )
    fun getGoodsSales(): McpResponse<GoodsSalesResponse> {
        val callerUserId = resolveCallerUserId()
        val result = getGoodsSalesUseCase.execute(callerUserId)
        return McpResponse.ok(data = result)
    }

    private fun resolveCallerUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        return principal.userId
    }
}
