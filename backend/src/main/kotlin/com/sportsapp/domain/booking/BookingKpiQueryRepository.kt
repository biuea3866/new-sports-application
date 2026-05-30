package com.sportsapp.domain.booking

import java.time.ZonedDateTime

interface BookingKpiQueryRepository {
    fun countConfirmedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun countRefundedByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime): Long
    fun findTopFacilityIdsByOwnerUserIdAndDateRange(ownerUserId: Long, from: ZonedDateTime, to: ZonedDateTime, limit: Int): List<String>
}
