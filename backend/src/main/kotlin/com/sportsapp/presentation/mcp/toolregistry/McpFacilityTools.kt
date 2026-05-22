package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.facility.FacilityCriteria
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.ListFacilitiesUseCase
import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.response.McpPagination
import com.sportsapp.presentation.mcp.response.McpResponse
import jakarta.servlet.http.HttpServletRequest
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.ZonedDateTime

/**
 * MCP Read tool — 시설(Facility) 조회.
 * scope: read:facility
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 try/finally 로 audit log 를 적재합니다.
 */
@Component
@Profile("!test-jpa")
class McpFacilityTools(
    private val listFacilitiesUseCase: ListFacilitiesUseCase,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {
    @PreAuthorize("@authz.hasMcpScope('read:facility')")
    @Tool(
        name = "getFacilities",
        description = "B2B 운영자가 등록한 스포츠 시설 목록을 조회합니다. gu(구 이름)와 type(시설 유형)으로 필터링할 수 있습니다.",
    )
    fun getFacilities(
        gu: String?,
        type: String?,
        page: Int,
        size: Int,
    ): McpResponse<List<FacilityResponse>> {
        val calledAt = ZonedDateTime.now()
        val startNano = System.nanoTime()
        val (tokenId, userId) = extractPrincipal()
        val namedParams = mapOf("gu" to gu, "type" to type, "page" to page, "size" to size)
        val (ipAddr, clientUserAgent) = extractHttpContext()

        var statusCode = 200
        try {
            val effectiveSize = size.coerceIn(1, FacilityCriteria.MAX_PAGE_SIZE)
            val criteria = FacilityCriteria(
                gu = gu,
                type = type,
                page = page,
                size = effectiveSize,
            )
            val resultPage = listFacilitiesUseCase.execute(criteria)
            val pagination = McpPagination.of(
                page = resultPage.number,
                size = resultPage.size,
                total = resultPage.totalElements,
            )
            return McpResponse.ok(data = resultPage.content, pagination = pagination)
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
                userId = userId,
                toolName = "getFacilities",
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
