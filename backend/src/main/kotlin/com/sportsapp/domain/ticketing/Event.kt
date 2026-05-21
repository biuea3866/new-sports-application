package com.sportsapp.domain.ticketing

import com.sportsapp.domain.common.JpaAuditingBase
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
    var title: String,

    @Column(nullable = false, length = 200)
    var venue: String,

    @Column(name = "starts_at", nullable = false)
    var startsAt: ZonedDateTime,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: EventStatus,

    @Column(name = "owner_id", nullable = false)
    val ownerId: Long,
) : JpaAuditingBase() {

    companion object {
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
    }

    fun openSales() {
        status.requireCanTransitTo(EventStatus.OPEN)
        status = EventStatus.OPEN
    }

    fun close() {
        status.requireCanTransitTo(EventStatus.CLOSED)
        status = EventStatus.CLOSED
    }

    fun update(newTitle: String, newVenue: String, newStartsAt: ZonedDateTime) {
        requireScheduled()
        title = newTitle
        venue = newVenue
        startsAt = newStartsAt
    }

    fun requireScheduled() {
        if (status != EventStatus.SCHEDULED) {
            throw com.sportsapp.domain.ticketing.exception.InvalidEventStateException(
                "Event $id is not in SCHEDULED state"
            )
        }
    }
}
