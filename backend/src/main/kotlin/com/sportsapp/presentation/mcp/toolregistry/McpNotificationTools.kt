package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.notification.dto.ListMyNotificationsCommand
import com.sportsapp.application.notification.usecase.ListMyNotificationsUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.response.McpPagination
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 알림(Notification) 목록 조회.
 * scope: read:notification
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 * IDOR 방지: userId 를 LLM 파라미터로 받지 않고 SecurityContext 의 McpAuthenticatedPrincipal.userId 사용.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 audit log 를 적재합니다.
 */
@Component
@Profile("!test-jpa")
class McpNotificationTools(
    private val listMyNotificationsUseCase: ListMyNotificationsUseCase,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {
    @PreAuthorize("@authz.hasMcpScope('read:notification')")
    @Tool(
        name = "getNotifications",
        description = "인증된 사용자의 알림 목록을 조회합니다. onlyUnread=true로 미읽음 알림만 필터링할 수 있습니다.",
    )
    fun getNotifications(
        onlyUnread: Boolean,
        page: Int = 0,
        size: Int = 20,
    ): McpResponse<List<McpNotificationItemResponse>> =
        mcpAuditLogAsyncRecorder.withAudit(
            toolName = "getNotifications",
            namedParams = mapOf("onlyUnread" to onlyUnread, "page" to page, "size" to size),
        ) {
            val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
                ?: throw AccessDeniedException("MCP authentication required")
            val command = ListMyNotificationsCommand(
                userId = principal.userId,
                onlyUnread = onlyUnread,
                page = page,
                size = size.coerceIn(1, 100),
            )
            val result = listMyNotificationsUseCase.execute(command)
            val pagination = McpPagination.of(page = result.page, size = result.size, total = result.totalElements)
            McpResponse.ok(data = result.content.map { McpNotificationItemResponse.of(it) }, pagination = pagination)
        }
}
