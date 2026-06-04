package com.sportsapp.presentation.mcp.admin

import com.sportsapp.application.mcp.ListMcpAuditLogsCommand
import com.sportsapp.application.mcp.ListMcpAuditLogsResponse
import com.sportsapp.application.mcp.ListMcpAuditLogsUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/api/admin/mcp/audit-logs")
class McpAuditLogAdminApiController(
    private val listMcpAuditLogsUseCase: ListMcpAuditLogsUseCase,
) {
    @GetMapping
    fun listAuditLogs(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) from: ZonedDateTime,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) to: ZonedDateTime,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<ListMcpAuditLogsResponse> {
        val command = ListMcpAuditLogsCommand(
            userId = principal.id,
            startCalledAt = from,
            endCalledAt = to,
            page = page,
            size = size,
        )
        return ResponseEntity.ok(listMcpAuditLogsUseCase.execute(command))
    }
}
