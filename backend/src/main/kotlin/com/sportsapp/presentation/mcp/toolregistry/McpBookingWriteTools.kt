package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.CancelBookingCommand
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenContext
import com.sportsapp.domain.mcp.confirm.ConfirmationTokenGateway
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component
import java.security.MessageDigest

/**
 * MCP Write tool — 예약(Booking) 변경.
 * scope: write:booking
 *
 * 2-step confirm flow:
 *   1차 호출 (confirmationToken = null) → 토큰 발급 + CONFIRM_REQUIRED 반환
 *   2차 호출 (confirmationToken = 발급 토큰) → 토큰 소진 + UseCase 실행 + OK 반환
 */
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
    fun cancelBooking(
        bookingId: Long,
        userId: Long,
        reason: String?,
        confirmationToken: String?,
    ): McpResponse<*> {
        if (confirmationToken == null) {
            val token = confirmationTokenGateway.issue(
                ConfirmationTokenContext(
                    toolName = "cancelBooking",
                    userId = userId,
                    paramsHash = hashParams("cancelBooking", bookingId, userId),
                )
            )
            return McpResponse.confirmRequired(
                data = mapOf(
                    "confirmationToken" to token,
                    "bookingId" to bookingId,
                    "userId" to userId,
                    "reason" to (reason ?: ""),
                    "message" to "예약 $bookingId 를 취소하시겠습니까? confirmationToken 을 포함해 재호출하면 실제로 취소됩니다.",
                ),
            )
        }

        confirmationTokenGateway.consume(confirmationToken)
        val result = cancelBookingUseCase.execute(
            CancelBookingCommand(
                bookingId = bookingId,
                cancelledByUserId = userId,
                reason = reason,
            )
        )
        return McpResponse.ok(data = result)
    }

    private fun hashParams(vararg parts: Any?): String {
        val raw = parts.joinToString(":") { it?.toString() ?: "" }
        val digest = MessageDigest.getInstance("SHA-256").digest(raw.toByteArray())
        return digest.take(8).joinToString("") { "%02x".format(it) }
    }
}
