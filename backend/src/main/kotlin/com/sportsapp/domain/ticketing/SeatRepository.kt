package com.sportsapp.domain.ticketing

interface SeatRepository {
    fun saveAll(seats: List<Seat>): List<Seat>
    fun findByEventId(eventId: Long): List<Seat>
    fun softDeleteByEventId(eventId: Long, deletedBy: Long?)
}
