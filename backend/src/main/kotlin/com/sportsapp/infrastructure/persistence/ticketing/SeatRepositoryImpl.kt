package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.SeatRepository
import org.springframework.stereotype.Component

@Component
class SeatRepositoryImpl(
    private val seatJpaRepository: SeatJpaRepository,
) : SeatRepository {

    override fun saveAll(seats: List<Seat>): List<Seat> =
        seatJpaRepository.saveAll(seats)

    override fun findByEventId(eventId: Long): List<Seat> =
        seatJpaRepository.findByEventIdAndDeletedAtIsNullOrderBySectionAscRowNoAscSeatNoAsc(eventId)

    override fun softDeleteByEventId(eventId: Long, deletedBy: Long?) {
        val seats = seatJpaRepository.findByEventIdAndDeletedAtIsNull(eventId)
        seats.forEach { it.softDelete(deletedBy) }
        seatJpaRepository.saveAll(seats)
    }
}
