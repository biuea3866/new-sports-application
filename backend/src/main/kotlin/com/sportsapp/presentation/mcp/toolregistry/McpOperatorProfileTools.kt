package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.dashboard.DashboardSummaryResponse
import com.sportsapp.application.dashboard.GetMyDashboardSummaryUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 운영자 프로파일(대시보드 요약) 조회.
 * scope: read:operator:profile
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 * IDOR 방지: userId 를 LLM 파라미터로 받지 않고 SecurityContext 의 McpAuthenticatedPrincipal.userId 사용.
 * 운영자 본인의 집계 데이터(시설/이벤트/상품 수)이므로 PII 마스킹 불필요.
 */
@Component
@Profile("!test-jpa")
class McpOperatorProfileTools(
    private val getMyDashboardSummaryUseCase: GetMyDashboardSummaryUseCase,
) {
    @PreAuthorize("@authz.hasMcpScope('read:operator:profile')")
    @Tool(
        name = "getOperatorProfile",
        description = "인증된 운영자의 프로파일을 조회합니다. 시설(FACILITY_OWNER), 이벤트(EVENT_HOST), 상품(GOODS_SELLER) 역할에 따른 대시보드 요약을 포함합니다.",
    )
    fun getOperatorProfile(): McpResponse<DashboardSummaryResponse> {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        val summary = getMyDashboardSummaryUseCase.execute(principal.userId)
        return McpResponse.ok(data = summary)
    }
}
