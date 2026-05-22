package com.sportsapp.presentation.mcp.toolregistry

import com.sportsapp.application.facility.FacilityCriteria
import com.sportsapp.application.facility.FacilityResponse
import com.sportsapp.application.facility.ListFacilitiesUseCase
import com.sportsapp.presentation.mcp.response.McpPagination
import com.sportsapp.presentation.mcp.response.McpResponse
import org.springframework.ai.tool.annotation.Tool
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * MCP Read tool — 시설(Facility) 조회.
 * scope: read:facility
 *
 * presentation layer 에 위치. UseCase 를 호출하고 McpResponse 로 래핑.
 */
@Component
@Profile("!test-jpa")
class McpFacilityTools(
    private val listFacilitiesUseCase: ListFacilitiesUseCase,
) {
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
    }
}
