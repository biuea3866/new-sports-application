package com.sportsapp.domain.ticketing

interface SeatCustomRepository {
    fun countSoldByEventId(eventId: Long): Long
    fun sumTotalSeatsByOwnerId(ownerId: Long): Long
    fun sumSoldSeatsByOwnerId(ownerId: Long): Long
}
