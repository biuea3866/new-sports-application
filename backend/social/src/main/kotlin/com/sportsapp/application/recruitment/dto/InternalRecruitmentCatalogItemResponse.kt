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
 *
 * [scheduledAt]은 `Recruitment.activityAt`(모임 활동 일시)이다 — 같은 제목의 모집글을 통합
 * 카탈로그에서 사용자가 구분하는 근거라 공급자가 채운다. `locationName`은 recruitment 가 채울
 * 자기 데이터가 없어(항상 null) 이 응답에 두지 않는다 — 상수 판정은 edge 파사드가 한다.
 */
data class InternalRecruitmentCatalogItemResponse(
    val sourceId: Long,
    val title: String,
    val price: BigDecimal,
    val status: RecruitmentStatus,
    val createdAt: ZonedDateTime,
    val scheduledAt: ZonedDateTime,
) {
    companion object {
        fun of(recruitment: Recruitment): InternalRecruitmentCatalogItemResponse = InternalRecruitmentCatalogItemResponse(
            sourceId = recruitment.id,
            title = recruitment.title,
            price = recruitment.feeAmount,
            status = recruitment.status,
            createdAt = recruitment.createdAt,
            scheduledAt = recruitment.activityAt,
        )
    }
}
