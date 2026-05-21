package com.sportsapp.domain.ticketing

interface CustomSeatRepository {
    fun countSoldByEventId(eventId: Long): Long
    fun sumTotalSeatsByOwnerId(ownerId: Long): Long
    fun sumSoldSeatsByOwnerId(ownerId: Long): Long
}
