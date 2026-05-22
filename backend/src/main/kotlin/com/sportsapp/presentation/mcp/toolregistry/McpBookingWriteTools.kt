package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.CancelBookingCommand
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.domain.mcp.confirm.ConfirmationParamsMismatchException
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

@Component
@Profile("!test-jpa")
class McpBookingWriteTools(
    private val cancelBookingUseCase: CancelBookingUseCase,
    private val confirmationTokenGateway: ConfirmationTokenGateway,
) {

    @PreAuthorize("@authz.hasMcpScope('write:booking')")
    @Tool(
        name = "cancelBooking",
        description = "예약을 취소합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 취소됩니다.",
    )
    fun cancelBooking(bookingId: Long, reason: String?, confirmationToken: String?): McpResponse<*> {
        val callerUserId = resolveCallerUserId()
        if (confirmationToken == null) return issueCancelBookingConfirmation(bookingId, callerUserId, reason)
        return executeCancelBooking(bookingId, callerUserId, reason, confirmationToken)
    }

    private fun issueCancelBookingConfirmation(bookingId: Long, callerUserId: Long, reason: String?): McpResponse<*> {
        val token = confirmationTokenGateway.issue(
            ConfirmationTokenContext(
                toolName = "cancelBooking",
                userId = callerUserId,
                paramsHash = McpParamsHasher.hash("cancelBooking", bookingId, callerUserId),
            )
        )
        return McpResponse.confirmRequired(
            data = mapOf(
                "confirmationToken" to token,
                "bookingId" to bookingId,
                "reason" to (reason ?: ""),
                "message" to "예약 $bookingId 를 취소하시겠습니까? confirmationToken 을 포함해 재호출하면 실제로 취소됩니다.",
            ),
        )
    }

    private fun executeCancelBooking(
        bookingId: Long,
        callerUserId: Long,
        reason: String?,
        confirmationToken: String,
    ): McpResponse<*> {
        val context = confirmationTokenGateway.consume(confirmationToken)
        val expectedHash = McpParamsHasher.hash("cancelBooking", bookingId, callerUserId)
        if (context.paramsHash != expectedHash) throw ConfirmationParamsMismatchException(confirmationToken)
        val result = cancelBookingUseCase.execute(
            CancelBookingCommand(bookingId = bookingId, cancelledByUserId = callerUserId, reason = reason)
        )
        return McpResponse.ok(data = result)
    }

    private fun resolveCallerUserId(): Long {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        return principal.userId
    }
}
