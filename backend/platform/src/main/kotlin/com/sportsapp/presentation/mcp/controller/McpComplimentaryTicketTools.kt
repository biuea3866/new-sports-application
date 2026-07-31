package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.ticketing.dto.IssueComplimentaryTicketCommand
import com.sportsapp.application.ticketing.usecase.IssueComplimentaryTicketUseCase
import com.sportsapp.domain.mcp.gateway.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.dto.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

/**
 * MCP Write tool — 무료 티켓(컴플리멘터리) 발급.
 * scope: write:ticket:complimentary
 *
 * 1차 호출(confirmationToken=null): confirm 토큰 발급 → statusCode=200, audit 적재
 * 2차 호출(confirmationToken 포함): 실제 발급 실행 → statusCode=200, audit 적재
 *
 * IDOR 방지: operatorUserId 를 LLM 파라미터로 받지 않고 SecurityContext 에서 추출.
 */
@Component
@Profile("!test-jpa")
class McpComplimentaryTicketTools(
    private val issueComplimentaryTicketUseCase: IssueComplimentaryTicketUseCase,
    confirmationTokenGateway: ConfirmationTokenGateway,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) : McpWriteToolBase(confirmationTokenGateway) {

    @PreAuthorize("@authz.hasMcpScope('write:ticket:complimentary')")
    @Tool(
        name = "issueComplimentaryTicket",
        description = "이벤트에 무료 티켓(컴플리멘터리)을 발급합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 발급됩니다.",
    )
    fun issueComplimentaryTicket(
        eventId: Long,
        seatId: Long,
        confirmationToken: String?,
    ): McpResponse<*> =
        mcpAuditLogAsyncRecorder.withAudit(
            toolName = "issueComplimentaryTicket",
            namedParams = mapOf(
                "eventId" to eventId,
                "seatId" to seatId,
                "confirmationToken" to if (confirmationToken != null) "[present]" else null,
            ),
        ) {
            val callerUserId = resolveCallerUserId()
            val paramsHash = McpParamsHasher.hash("issueComplimentaryTicket", callerUserId, eventId, seatId)
            if (confirmationToken == null) {
                return@withAudit issueConfirmation(
                    toolName = "issueComplimentaryTicket",
                    userId = callerUserId,
                    paramsHash = paramsHash,
                    metadata = mapOf(
                        "eventId" to eventId,
                        "seatId" to seatId,
                        "message" to "이벤트 $eventId 의 좌석 $seatId 에 무료 티켓을 발급하시겠습니까?",
                    ),
                )
            }
            validateHashAndConsume(confirmationToken, paramsHash)
            val result = issueComplimentaryTicketUseCase.execute(
                IssueComplimentaryTicketCommand(eventId = eventId, seatId = seatId, operatorUserId = callerUserId)
            )
            McpResponse.ok(data = result)
        }

}
