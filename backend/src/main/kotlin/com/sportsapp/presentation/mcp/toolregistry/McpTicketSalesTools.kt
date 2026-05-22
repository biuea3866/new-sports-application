package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.ticketing.GetTicketSalesCommand
import com.sportsapp.application.ticketing.GetTicketSalesUseCase
import com.sportsapp.application.ticketing.TicketSalesResponse
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * MCP Read tool — 티켓(Ticketing) 판매 통계 조회.
 * scope: read:ticket:sales
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 */
@Component
@Profile("!test-jpa")
class McpTicketSalesTools(
    private val getTicketSalesUseCase: GetTicketSalesUseCase,
) {
    @PreAuthorize("@authz.hasMcpScope('read:ticket:sales')")
    @Tool(
        name = "getTicketSales",
        description = "B2B 운영자의 티켓 판매 통계를 조회합니다. ownerUserId·from·to는 필수이며, " +
            "eventId로 특정 이벤트만 필터링할 수 있습니다. from/to는 ISO-8601 형식입니다.",
    )
    fun getTicketSales(
        ownerUserId: Long,
        eventId: Long?,
        from: String,
        to: String,
    ): McpResponse<TicketSalesResponse> {
        val command = GetTicketSalesCommand(
            ownerUserId = ownerUserId,
            eventId = eventId,
            from = ZonedDateTime.parse(from),
            to = ZonedDateTime.parse(to),
        )
        val result = getTicketSalesUseCase.execute(command)
        return McpResponse.ok(data = result)
    }
}
