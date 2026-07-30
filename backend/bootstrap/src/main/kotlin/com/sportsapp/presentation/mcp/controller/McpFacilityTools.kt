package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.facility.dto.FacilityCriteria
import com.sportsapp.presentation.facility.dto.response.FacilityResponse
import com.sportsapp.application.facility.usecase.ListFacilitiesUseCase
import com.sportsapp.presentation.mcp.audit.McpAuditLogAsyncRecorder
import com.sportsapp.presentation.mcp.audit.McpToolAuditHelper.withAudit
import com.sportsapp.presentation.mcp.dto.response.McpPagination
import com.sportsapp.presentation.mcp.dto.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 시설(Facility) 조회.
 * scope: read:facility
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 *
 * Pattern B: Spring AI MethodToolCallback 은 CGLIB proxy 를 우회하므로
 * McpAuditLogAsyncRecorder 를 생성자로 직접 주입하여 audit log 를 적재합니다.
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
        description = "B2B 운영자가 등록한 스포츠 시설 목록을 조회합니다. " +
            "sidoCode(시도 표준코드)·sigunguCode(시군구 표준코드)·gu(구 이름)·type(시설 유형)으로 필터링할 수 있습니다.",
    )
    fun getFacilities(
        sidoCode: String?,
        sigunguCode: String?,
        gu: String?,
        type: String?,
        page: Int,
        size: Int,
    ): McpResponse<List<FacilityResponse>> =
        mcpAuditLogAsyncRecorder.withAudit(
            toolName = "getFacilities",
            namedParams = mapOf(
                "sidoCode" to sidoCode,
                "sigunguCode" to sigunguCode,
                "gu" to gu,
                "type" to type,
                "page" to page,
                "size" to size,
            ),
        ) {
            val effectiveSize = size.coerceIn(1, FacilityCriteria.MAX_PAGE_SIZE)
            val criteria = FacilityCriteria(
                sidoCode = sidoCode,
                sigunguCode = sigunguCode,
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
            McpResponse.ok(data = resultPage.content.map { FacilityResponse.of(it) }, pagination = pagination)
        }
}
