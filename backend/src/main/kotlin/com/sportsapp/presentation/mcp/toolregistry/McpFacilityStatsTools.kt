package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.facility.GetGuTypeStatsUseCase
import com.sportsapp.application.facility.GuTypeCountResponse
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 시설 구/유형별 통계 조회.
 * scope: read:facility:stats
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 * 집계 데이터이므로 PII 마스킹 불필요.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 audit log 를 적재합니다.
 */
@Component
@Profile("!test-jpa")
class McpFacilityStatsTools(
    private val getGuTypeStatsUseCase: GetGuTypeStatsUseCase,
    private val mcpAuditLogAsyncRecorder: McpAuditLogAsyncRecorder,
) {
    @PreAuthorize("@authz.hasMcpScope('read:facility:stats')")
    @Tool(
        name = "getFacilityStats",
        description = "구(gu)와 시설 유형(type)별 시설 수 통계를 조회합니다. 필터 없이 전체 집계 결과를 반환합니다.",
    )
    fun getFacilityStats(): McpResponse<List<GuTypeCountResponse>> =
        mcpAuditLogAsyncRecorder.withAudit("getFacilityStats", emptyMap()) {
            val stats = getGuTypeStatsUseCase.execute()
            McpResponse.ok(data = stats)
        }
}
