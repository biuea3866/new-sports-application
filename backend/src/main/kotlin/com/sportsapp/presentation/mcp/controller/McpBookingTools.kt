package com.sportsapp.presentation.mcp.controller

import com.sportsapp.presentation.booking.dto.response.BookingResponse
import com.sportsapp.application.booking.dto.ListBookingsCommand
import com.sportsapp.application.booking.usecase.ListMyBookingsUseCase
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.dto.response.McpPagination
import com.sportsapp.presentation.mcp.dto.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 예약(Booking) 조회.
 * scope: read:booking
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 audit log 를 적재합니다.
 */
@Component
@Profile("!test-jpa")
class McpBookingTools(
    private val listMyBookingsUseCase: ListMyBookingsUseCase,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {
    @PreAuthorize("@authz.hasMcpScope('read:booking')")
    @Tool(
        name = "getBookings",
        description = "특정 사용자의 예약 목록을 조회합니다. userId는 필수이며, status(PENDING/CONFIRMED/CANCELLED)로 필터링할 수 있습니다.",
    )
    fun getBookings(
        userId: Long,
        status: String?,
        page: Int,
        size: Int,
    ): McpResponse<List<BookingResponse>> =
        mcpAuditLogAsyncRecorder.withAudit(
            toolName = "getBookings",
            namedParams = mapOf("userId" to userId, "status" to status, "page" to page, "size" to size),
        ) {
            val bookingStatus = status?.let { BookingStatus.valueOf(it) }
            val command = ListBookingsCommand(
                userId = userId,
                status = bookingStatus,
                pageable = PageRequest.of(page, size.coerceIn(1, 100)),
            )
            val result = listMyBookingsUseCase.execute(command)
            val pagination = McpPagination.of(page = result.page, size = result.size, total = result.totalElements)
            McpResponse.ok(data = result.bookings.map { BookingResponse.of(it) }, pagination = pagination)
        }
}
