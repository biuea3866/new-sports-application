package com.sportsapp.domain.community.dto

import com.sportsapp.domain.community.entity.CommunityBooking
import com.sportsapp.domain.community.gateway.SlotInfo
import java.time.ZonedDateTime

/**
 * 연결 예약 조회 결과 — [CommunityBooking] 링크와 [SlotInfo] 표시정보를 평탄화한 DomainService 반환 DTO.
 * SlotInfo가 없으면(슬롯 삭제 등) 표시 필드는 null이다.
 */
data class CommunityBookingResult(
    val id: Long,
    val communityId: Long,
    val slotId: Long,
    val linkedByUserId: Long,
    val facilityId: String?,
    val date: ZonedDateTime?,
    val timeRange: String?,
    val capacity: Int?,
) {
    companion object {
        fun of(booking: CommunityBooking, slotInfo: SlotInfo?): CommunityBookingResult = CommunityBookingResult(
            id = booking.id,
            communityId = booking.communityId,
            slotId = booking.slotId,
            linkedByUserId = booking.linkedByUserId,
            facilityId = slotInfo?.facilityId,
            date = slotInfo?.date,
            timeRange = slotInfo?.timeRange,
            capacity = slotInfo?.capacity,
        )
    }
}
