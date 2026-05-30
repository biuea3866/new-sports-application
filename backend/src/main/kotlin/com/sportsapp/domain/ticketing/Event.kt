package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.JpaAuditingBase
import com.sportsapp.domain.ticketing.exception.InvalidEventStateException
import com.sportsapp.domain.ticketing.exception.TooManySeatsException
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "events")
class Event(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(nullable = false, length = 200)
    val title: String,

    @Column(nullable = false, length = 200)
    val venue: String,

    @Column(name = "starts_at", nullable = false)
    val startsAt: ZonedDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EventStatus,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
) : JpaAuditingBase() {

    companion object {
        private const val MAX_SEATS_PER_EVENT = 500

        fun create(
            title: String,
            venue: String,
            startsAt: ZonedDateTime,
            ownerId: Long,
        ): Event = Event(
            id = 0L,
            title = title,
            venue = venue,
            startsAt = startsAt,
            status = EventStatus.SCHEDULED,
            ownerId = ownerId,
        )

        fun validateSeatLimit(seatSpecs: List<*>) {
            if (seatSpecs.size > MAX_SEATS_PER_EVENT) {
                throw TooManySeatsException(seatSpecs.size, MAX_SEATS_PER_EVENT)
            }
        }
    }

    fun requireOwnedBy(userId: Long) {
        if (ownerId != userId) throw EventOwnershipException(id)
    }

    fun requireDeletable() {
        check(!isDeleted) { "Event is already deleted" }
        if (status != EventStatus.SCHEDULED) {
            throw InvalidEventStateException("Cannot delete event in $status state")
        }
    }

    fun openSales() {
        status.requireCanTransitTo(EventStatus.OPEN)
        status = EventStatus.OPEN
    }

    fun close() {
        status.requireCanTransitTo(EventStatus.CLOSED)
        status = EventStatus.CLOSED
    }
}
