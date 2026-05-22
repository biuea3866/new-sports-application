package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.ticketing.GetTicketSalesCommand
import com.sportsapp.application.ticketing.GetTicketSalesUseCase
import com.sportsapp.application.ticketing.TicketSalesResponse
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * MCP Read tool — 티켓(Ticketing) 판매 통계 조회.
 * scope: read:ticket:sales
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 * IDOR 방지: ownerUserId 를 LLM 파라미터로 받지 않고 SecurityContext 의 McpAuthenticatedPrincipal.userId 사용.
 */
@Component
@Profile("!test-jpa")
class McpTicketSalesTools(
    private val getTicketSalesUseCase: GetTicketSalesUseCase,
) {
    @PreAuthorize("@authz.hasMcpScope('read:ticket:sales')")
    @Tool(
        name = "getTicketSales",
        description = "인증된 B2B 운영자의 티켓 판매 통계를 조회합니다. from·to는 필수이며, " +
            "eventId로 특정 이벤트만 필터링할 수 있습니다. from/to는 ISO-8601 형식입니다.",
    )
    fun getTicketSales(
        eventId: Long?,
        from: String,
        to: String,
    ): McpResponse<TicketSalesResponse> {
        val callerUserId = resolveCallerUserId()
        val command = GetTicketSalesCommand(
            ownerUserId = callerUserId,
            eventId = eventId,
            from = ZonedDateTime.parse(from),
            to = ZonedDateTime.parse(to),
        )
        val result = getTicketSalesUseCase.execute(command)
        return McpResponse.ok(data = result)
    }

    private fun resolveCallerUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        return principal.userId
    }
}
