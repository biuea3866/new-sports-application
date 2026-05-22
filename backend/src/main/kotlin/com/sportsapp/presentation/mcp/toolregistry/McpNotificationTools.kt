package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.notification.ListMyNotificationsCommand
import com.sportsapp.application.notification.ListMyNotificationsUseCase
import com.sportsapp.domain.mcp.McpAuthenticatedPrincipal
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
 * 알림 데이터는 templateId/status 등 집계 정보이며 B2C PII를 직접 노출하지 않으므로
 * PiiMasker 적용 불필요.
 */
@Component
@Profile("!test-jpa")
class McpNotificationTools(
    private val listMyNotificationsUseCase: ListMyNotificationsUseCase,
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
    ): McpResponse<List<McpNotificationItemResponse>> {
        val principal = SecurityContextHolder.getContext().authentication?.principal as? McpAuthenticatedPrincipal
            ?: throw AccessDeniedException("MCP authentication required")
        val command = ListMyNotificationsCommand(
            userId = principal.userId,
            onlyUnread = onlyUnread,
            page = page,
            size = size.coerceIn(1, 100),
        )
        val result = listMyNotificationsUseCase.execute(command)
        val pagination = McpPagination.of(
            page = result.page,
            size = result.size,
            total = result.totalElements,
        )
        return McpResponse.ok(data = result.content.map { McpNotificationItemResponse.of(it) }, pagination = pagination)
    }
}
