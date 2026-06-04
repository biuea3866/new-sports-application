package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.mcp.dto.GetMcpUsageAnalyticsCommand
import com.sportsapp.application.mcp.dto.GetMcpUsageAnalyticsResponse
import com.sportsapp.application.mcp.usecase.GetMcpUsageAnalyticsUseCase
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
@RequestMapping("/api/admin/mcp/usage-analytics")
class McpUsageAnalyticsApiController(
    private val getMcpUsageAnalyticsUseCase: GetMcpUsageAnalyticsUseCase,
) {
    /**
     * MCP 사용 분석 집계 조회.
     *
     * IDOR 차단: [principal].id 를 userId 필터로 강제 — 운영자 본인 데이터만 반환.
     *
     * 기간 파라미터:
     * - [from]: 집계 시작 시각 (ISO 8601, 예: 2026-05-01T00:00:00Z)
     * - [to]: 집계 종료 시각 (ISO 8601, 예: 2026-05-31T23:59:59Z)
     * - 기본값(미지정) 시 from = 90일 전, to = 현재
     */
    @GetMapping
    fun getUsageAnalytics(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        from: ZonedDateTime?,
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        to: ZonedDateTime?,
    ): ResponseEntity<GetMcpUsageAnalyticsResponse> {
        val now = ZonedDateTime.now()
        val command = GetMcpUsageAnalyticsCommand(
            userId = principal.id,
            from = from ?: now.minusDays(90),
            to = to ?: now,
        )
        return ResponseEntity.ok(getMcpUsageAnalyticsUseCase.execute(command))
    }
}
