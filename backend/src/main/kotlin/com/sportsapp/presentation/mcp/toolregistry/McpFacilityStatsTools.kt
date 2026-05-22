package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.facility.GetGuTypeStatsUseCase
import com.sportsapp.application.facility.GuTypeCountResponse
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
 */
@Component
@Profile("!test-jpa")
class McpFacilityStatsTools(
    private val getGuTypeStatsUseCase: GetGuTypeStatsUseCase,
) {
    @PreAuthorize("@authz.hasMcpScope('read:facility:stats')")
    @Tool(
        name = "getFacilityStats",
        description = "구(gu)와 시설 유형(type)별 시설 수 통계를 조회합니다. 필터 없이 전체 집계 결과를 반환합니다.",
    )
    fun getFacilityStats(): McpResponse<List<GuTypeCountResponse>> {
        val stats = getGuTypeStatsUseCase.execute()
        return McpResponse.ok(data = stats)
    }
}
