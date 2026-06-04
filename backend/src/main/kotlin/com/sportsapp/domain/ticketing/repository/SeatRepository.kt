package com.sportsapp.domain.ticketing.repository

import com.sportsapp.domain.ticketing.entity.Seat

interface SeatRepository {
    fun saveAll(seats: List<Seat>): List<Seat>
    fun findByEventId(eventId: Long): List<Seat>
    fun softDeleteByEventId(eventId: Long, deletedBy: Long?)
}
