package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.ListBookingsCommand
import com.sportsapp.application.booking.ListMyBookingsUseCase
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.presentation.mcp.response.McpPagination
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 예약(Booking) 조회.
 * scope: read:booking
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 */
@Component
class McpBookingTools(
    private val listMyBookingsUseCase: ListMyBookingsUseCase,
) {
    @Tool(
        name = "getBookings",
        description = "특정 사용자의 예약 목록을 조회합니다. userId는 필수이며, status(PENDING/CONFIRMED/CANCELLED)로 필터링할 수 있습니다.",
    )
    fun getBookings(
        userId: Long,
        status: String?,
        page: Int,
        size: Int,
    ): McpResponse<List<BookingResponse>> {
        val bookingStatus = status?.let { BookingStatus.valueOf(it) }
        val command = ListBookingsCommand(
            userId = userId,
            status = bookingStatus,
            pageable = PageRequest.of(page, size.coerceIn(1, 100)),
        )
        val result = listMyBookingsUseCase.execute(command)
        val pagination = McpPagination.of(
            page = result.page,
            size = result.size,
            total = result.totalElements,
        )
        return McpResponse.ok(data = result.bookings, pagination = pagination)
    }
}
