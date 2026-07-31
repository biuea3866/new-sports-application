package com.sportsapp.application.featureflag.dto

import com.sportsapp.domain.featureflag.entity.FeatureFlagAuditLog
import org.springframework.data.domain.Page

/**
 * 감사 로그 페이징 응답 — 레포 `ListMcpAuditLogsResponse` 선례를 미러링한다.
 * FE의 총페이지 표시("1/3")를 위해 total 계열 필드를 포함한다(senior-pm 정합 #1).
 */
data class ListFeatureFlagAuditLogsResponse(
    val content: List<FeatureFlagAuditLogResponse>,
    val totalElements: Long,
    val totalPages: Int,
    val pageNumber: Int,
    val pageSize: Int,
) {
    companion object {
        fun of(page: Page<FeatureFlagAuditLog>): ListFeatureFlagAuditLogsResponse = ListFeatureFlagAuditLogsResponse(
            content = page.content.map { FeatureFlagAuditLogResponse.of(it) },
            totalElements = page.totalElements,
            totalPages = page.totalPages,
            pageNumber = page.number,
            pageSize = page.size,
        )
    }
}
