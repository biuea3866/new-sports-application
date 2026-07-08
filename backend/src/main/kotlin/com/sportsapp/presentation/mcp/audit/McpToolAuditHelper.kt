package com.sportsapp.presentation.mcp.audit

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.mcp.vo.McpAuthenticatedPrincipal
import jakarta.servlet.http.HttpServletRequest
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.time.ZonedDateTime

/**
 * MCP tool audit 공통 헬퍼.
 *
 * Pattern B 에서 12개 tool bean 에 동일하게 필요한
 * principal 추출 / HTTP context 추출 / audit 래핑 로직을 한 곳에서 관리합니다.
 *
 * object 로 선언 — 상태 없음, DI 불필요.
 * 위치: presentation/mcp/audit (tool 과 같은 presentation layer)
 */
object McpToolAuditHelper {

    fun extractPrincipal(): Pair<Long?, Long> {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        return if (principal is McpAuthenticatedPrincipal) principal.tokenId to principal.userId
        else null to 0L
    }

    fun extractHttpContext(): Pair<String?, String?> {
        val attributes = RequestContextHolder.getRequestAttributes() as? ServletRequestAttributes
        val request = attributes?.request
        return extractIpAddr(request) to request?.getHeader("User-Agent")
    }

    private fun extractIpAddr(request: HttpServletRequest?): String? {
        if (request == null) return null
        val forwarded = request.getHeader("X-Forwarded-For")
        return forwarded?.split(",")?.firstOrNull()?.trim() ?: request.remoteAddr
    }

    @PublishedApi
    internal fun resolveStatusCode(exception: Exception): Int = when (exception) {
        is BusinessException -> exception.status.httpStatus
        is AccessDeniedException -> 403
        else -> 500
    }

    /**
     * tool 함수 실행을 audit log 로 감쌉니다.
     *
     * exception 은 그대로 re-throw 되므로 tool 동작에 영향 없습니다.
     * finally 블록에서 항상 audit log 를 적재합니다.
     */
    inline fun <T> McpAuditLogAsyncRecorder.withAudit(
        toolName: String,
        namedParams: Map<String, Any?>,
        block: () -> T,
    ): T {
        val calledAt = ZonedDateTime.now()
        val startNano = System.nanoTime()
        val (tokenId, userId) = extractPrincipal()
        val (ipAddr, clientUserAgent) = extractHttpContext()
        var statusCode = 200
        try {
            return block()
        } catch (exception: Exception) {
            statusCode = resolveStatusCode(exception)
            throw exception
        } finally {
            val latencyMs = ((System.nanoTime() - startNano) / 1_000_000).toInt()
            record(tokenId, userId, toolName, namedParams, statusCode, latencyMs, ipAddr, clientUserAgent, calledAt)
        }
    }
}
