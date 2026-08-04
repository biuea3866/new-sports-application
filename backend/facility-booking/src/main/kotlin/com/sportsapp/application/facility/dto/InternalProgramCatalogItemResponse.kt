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
 *
 * [locationName]은 시설명이다 — 시설 4곳이 각자 같은 이름의 시설상품("주말 정기 레슨")을 등록할 수
 * 있어, 통합 카탈로그에서 사용자가 항목을 구분하려면 필요한 **실데이터**라 공급자가 채운다
 * (상수 판정만 edge 파사드가 맡는다). 참조 시설이 삭제돼 이름을 찾지 못하면 null 그대로 노출한다 —
 * 빈 문자열·유형명 반복으로 메우지 않는다. `scheduledAt`은 시설상품에 일정 개념이 없어(항상 null)
 * 이 응답에 두지 않는다.
 */
data class InternalProgramCatalogItemResponse(
    val sourceId: Long,
    val title: String,
    val price: BigDecimal,
    val createdAt: ZonedDateTime,
    val locationName: String?,
) {
    companion object {
        fun of(program: Program, facilityName: String?): InternalProgramCatalogItemResponse = InternalProgramCatalogItemResponse(
            sourceId = program.id,
            title = program.name,
            price = program.price,
            createdAt = program.createdAt,
            locationName = facilityName,
        )
    }
}
