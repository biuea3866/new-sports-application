package com.sportsapp.application.community.dto

import com.sportsapp.domain.community.dto.CommunityBookingResult
import com.sportsapp.domain.community.entity.CommunityBooking
import java.time.ZonedDateTime

/** TDD "응답 DTO — CommunityBookingResponse". `POST /communities/{id}/bookings` 응답. Controller가 그대로 반환한다. */
data class CommunityBookingResponse(
    val id: Long,
    val communityId: Long,
    val slotId: Long,
    val linkedByUserId: Long,
    val createdAt: ZonedDateTime,
) {
    companion object {
        fun of(booking: CommunityBooking): CommunityBookingResponse = CommunityBookingResponse(
            id = booking.id,
            communityId = booking.communityId,
            slotId = booking.slotId,
            linkedByUserId = booking.linkedByUserId,
            createdAt = booking.createdAt,
        )
    }
}

/** TDD "응답 DTO — CommunityBookingListItemResponse". `GET /communities/{id}/bookings` 목록 응답 항목. */
data class CommunityBookingListItemResponse(
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
        fun of(result: CommunityBookingResult): CommunityBookingListItemResponse = CommunityBookingListItemResponse(
            id = result.id,
            communityId = result.communityId,
            slotId = result.slotId,
            linkedByUserId = result.linkedByUserId,
            facilityId = result.facilityId,
            date = result.date,
            timeRange = result.timeRange,
            capacity = result.capacity,
        )
    }
}
