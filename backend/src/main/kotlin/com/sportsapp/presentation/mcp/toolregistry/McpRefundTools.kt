package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.dto.RefundBookingCommand
import com.sportsapp.application.booking.usecase.RefundBookingUseCase
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.confirm.McpParamsHasher
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * MCP Write tool — 예약 환불.
 * scope: write:booking:refund
 *
 * 1차 호출(confirmationToken=null): confirm 토큰 발급 → statusCode=200, audit 적재
 * 2차 호출(confirmationToken 포함): 실제 환불 실행 → statusCode=200, audit 적재
 *
 * 실 PG 통합은 Open Issue #7 (PG sandbox) 해소 후 후속 PR.
 * 현재는 StubPaymentRefundGateway(@Profile("!prod"))로 동작.
 */
@Component
@Profile("!test-jpa")
class McpRefundTools(
    private val refundBookingUseCase: RefundBookingUseCase,
    confirmationTokenGateway: ConfirmationTokenGateway,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) : McpWriteToolBase(confirmationTokenGateway) {

    @PreAuthorize("@authz.hasMcpScope('write:booking:refund')")
    @Tool(
        name = "refundBooking",
        description = "예약을 환불합니다. 최초 호출 시 확인 토큰이 발급됩니다. confirmationToken 을 포함해 재호출하면 실제로 환불됩니다.",
    )
    fun refundBooking(
        bookingId: Long,
        refundAmount: String,
        reason: String,
        confirmationToken: String?,
    ): McpResponse<*> =
        mcpAuditLogAsyncRecorder.withAudit(
            toolName = "refundBooking",
            namedParams = mapOf(
                "bookingId" to bookingId,
                "refundAmount" to refundAmount,
                "reason" to reason,
                "confirmationToken" to if (confirmationToken != null) "[present]" else null,
            ),
        ) {
            val callerUserId = resolveCallerUserId()
            val paramsHash = McpParamsHasher.hash("refundBooking", bookingId, callerUserId, refundAmount)
            if (confirmationToken == null) {
                return@withAudit issueRefundConfirmation(bookingId, refundAmount, reason, callerUserId, paramsHash)
            }
            validateHashAndConsume(confirmationToken, paramsHash)
            McpResponse.ok(data = refundBookingUseCase.execute(
                RefundBookingCommand(
                    bookingId = bookingId,
                    callerUserId = callerUserId,
                    refundAmount = BigDecimal(refundAmount),
                    reason = reason,
                )
            ))
        }

    private fun issueRefundConfirmation(
        bookingId: Long,
        refundAmount: String,
        reason: String,
        userId: Long,
        paramsHash: String,
    ): McpResponse<Map<String, Any>> = issueConfirmation(
        toolName = "refundBooking",
        userId = userId,
        paramsHash = paramsHash,
        metadata = mapOf(
            "bookingId" to bookingId,
            "refundAmount" to refundAmount,
            "reason" to reason,
            "message" to "예약 $bookingId 을(를) $refundAmount 원 환불하시겠습니까?",
        ),
    )

}
