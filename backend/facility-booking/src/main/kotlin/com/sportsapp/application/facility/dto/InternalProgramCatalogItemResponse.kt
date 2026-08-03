package com.sportsapp.application.facility.dto

import com.sportsapp.domain.facility.entity.Program
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * edge catalog 통합검색(BE-07)이 `CatalogSearchGateway.searchPrograms` 원격 구현(2단계)으로 소비할
 * 계약 응답 (S2-04). status="ACTIVE" 고정값 판정과 detailPath 조립은 edge 파사드가 수행하므로
 * 여기 포함하지 않는다 — 매핑 위치를 옮기면 섀도 응답 동일성 비교가 성립하지 않는다(S2-04 티켓
 * "변경 사항" 참고). [Program] 엔티티 자체(facilityId·ownerUserId·capacity·durationMinutes 등)를
 * 그대로 노출하지 않는다.
 */
data class InternalProgramCatalogItemResponse(
    val sourceId: Long,
    val title: String,
    val price: BigDecimal,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(program: Program): InternalProgramCatalogItemResponse = InternalProgramCatalogItemResponse(
            sourceId = program.id,
            title = program.name,
            price = program.price,
            createdAt = program.createdAt,
        )
    }
}
