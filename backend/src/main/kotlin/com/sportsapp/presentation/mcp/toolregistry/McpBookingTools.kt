package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.booking.BookingResponse
import com.sportsapp.application.booking.ListBookingsCommand
import com.sportsapp.application.booking.ListMyBookingsUseCase
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.response.McpPagination
import com.sportsapp.presentation.mcp.response.McpResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.ZonedDateTime

/**
 * MCP Read tool — 예약(Booking) 조회.
 * scope: read:booking
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 try/finally 로 audit log 를 적재합니다.
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
    ): McpResponse<List<BookingResponse>> {
        val calledAt = ZonedDateTime.now()
        val startNano = System.nanoTime()
        val (tokenId, callerUserId) = extractPrincipal()
        val namedParams = mapOf("userId" to userId, "status" to status, "page" to page, "size" to size)
        val (ipAddr, clientUserAgent) = extractHttpContext()

        var statusCode = 200
        try {
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
        } catch (exception: BusinessException) {
            statusCode = exception.status.httpStatus
            throw exception
        } catch (exception: AccessDeniedException) {
            statusCode = 403
            throw exception
        } catch (exception: RuntimeException) {
            statusCode = 500
            throw exception
        } finally {
            val latencyMs = ((System.nanoTime() - startNano) / 1_000_000).toInt()
            mcpAuditLogAsyncRecorder.record(
                tokenId = tokenId,
                userId = callerUserId,
                toolName = "getBookings",
                namedParams = namedParams,
                statusCode = statusCode,
                latencyMs = latencyMs,
                ipAddr = ipAddr,
                clientUserAgent = clientUserAgent,
                calledAt = calledAt,
            )
        }
    }

    private fun extractPrincipal(): Pair<Long?, Long> {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is McpAuthenticatedPrincipal) {
            principal.tokenId to principal.userId
        } else {
            null to 0L
        }
    }

    private fun extractHttpContext(): Pair<String?, String?> {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val request = attributes?.request
        return extractIpAddr(request) to request?.getHeader("User-Agent")
    }

    private fun extractIpAddr(request: HttpServletRequest?): String? {
        if (request == null) return null
        val forwarded = request.getHeader("X-Forwarded-For")
        return forwarded?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr
    }
}
