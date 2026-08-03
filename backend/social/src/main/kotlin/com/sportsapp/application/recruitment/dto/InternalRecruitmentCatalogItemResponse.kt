package com.sportsapp.application.recruitment.dto

import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * catalog 통합검색(BE-07)이 fan-out 하는 recruitment 원격 공급 응답 (S2-05).
 *
 * edge `CatalogSearchGateway.searchRecruitments`가 2단계에 이 응답을 그대로 소비한다 — 필드는
 * edge 소유 `CatalogItem`(S2-01)이 `Recruitment.toCatalogItem()`으로 채우는 값에서 역산했다.
 * `detailPath`·`itemType` 판정은 edge 파사드의 책임이라 이 응답에는 포함하지 않는다.
 */
data class InternalRecruitmentCatalogItemResponse(
    val sourceId: Long,
    val title: String,
    val feeAmount: BigDecimal,
    val status: RecruitmentStatus,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(recruitment: Recruitment): InternalRecruitmentCatalogItemResponse = InternalRecruitmentCatalogItemResponse(
            sourceId = recruitment.id,
            title = recruitment.title,
            feeAmount = recruitment.feeAmount,
            status = recruitment.status,
            createdAt = recruitment.createdAt,
        )
    }
}
