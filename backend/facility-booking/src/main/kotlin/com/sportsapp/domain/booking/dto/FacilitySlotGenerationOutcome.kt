package com.sportsapp.domain.booking.dto

/**
 * 시설 단위 자동 슬롯 생성 결과(BE-58).
 *
 * 한 시설의 생성 실패가 다른 시설 처리를 막지 않도록(부분실패 격리) [succeeded]로 성공 여부를 담는다.
 */
data class FacilitySlotGenerationOutcome(
    val facilityId: String,
    val createdCount: Int,
    val succeeded: Boolean,
)
