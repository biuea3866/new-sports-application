package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.goods.usecase.GetGoodsSalesUseCase
import com.sportsapp.application.goods.dto.GoodsSalesResult
import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.dto.response.McpResponse
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
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 audit log 를 적재합니다.
 */
@Component
@Profile("!test-jpa")
class McpGoodsSalesTools(
    private val getGoodsSalesUseCase: GetGoodsSalesUseCase,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {
    @PreAuthorize("@authz.hasMcpScope('read:goods:sales')")
    @Tool(
        name = "getGoodsSales",
        description = "인증된 B2B 운영자의 상품 판매 통계를 조회합니다. 활성 상품 수·품절 상품 수·확정 주문 수·총 매출액을 반환합니다.",
    )
    fun getGoodsSales(): McpResponse<GoodsSalesResult> =
        mcpAuditLogAsyncRecorder.withAudit("getGoodsSales", emptyMap()) {
            val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
                ?: throw AccessDeniedException("MCP authentication required")
            val result = getGoodsSalesUseCase.execute(principal.userId)
            McpResponse.ok(data = result)
        }
}
