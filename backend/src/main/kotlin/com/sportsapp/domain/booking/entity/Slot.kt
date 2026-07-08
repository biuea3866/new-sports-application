package com.sportsapp.domain.booking.entity

import com.sportsapp.domain.booking.exception.InvalidSlotException
import com.sportsapp.domain.booking.exception.InvalidSlotStatusException
import com.sportsapp.domain.booking.exception.SlotClosedException
import com.sportsapp.domain.booking.exception.UnauthorizedSlotAccessException
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
@Table(name = "slots")
class Slot private constructor(
    @Column(nullable = false)
    val facilityId: String,

    @Column(nullable = false)
    val date: ZonedDateTime,

    @Column(nullable = false)
    var timeRange: String,

    capacity: Int,

    @Column(nullable = false)
    val ownerId: Long,

    @Column(name = "program_id")
    val programId: Long?,

    status: SlotStatus,
) : JpaAuditingBase() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    val id: Long = 0

    @Column(nullable = false)
    var capacity: Int = capacity
        private set

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: SlotStatus = status
        private set

    fun requireOwnedBy(userId: Long) {
        if (ownerId != userId) throw UnauthorizedSlotAccessException(id)
    }

    fun applyUpdate(newTimeRange: String?, newCapacity: Int?) {
        newTimeRange?.let {
            if (!TIME_RANGE_REGEX.matches(it)) {
                throw InvalidSlotException("timeRange must be HH:mm-HH:mm format, got: $it")
            }
            timeRange = it
        }
        newCapacity?.let {
            if (it <= 0) throw InvalidSlotException("capacity must be positive, got: $it")
            capacity = it
        }
    }

    fun close(requesterId: Long) {
        requireOwnedBy(requesterId)
        transitionTo(SlotStatus.CLOSED)
    }

    fun open(requesterId: Long) {
        requireOwnedBy(requesterId)
        transitionTo(SlotStatus.OPEN)
    }

    fun requireBookable() {
        if (status == SlotStatus.CLOSED) throw SlotClosedException(id)
    }

    private fun transitionTo(target: SlotStatus) {
        if (!status.canTransitTo(target)) throw InvalidSlotStatusException(status, target)
        status = target
    }

    companion object {
        private val TIME_RANGE_REGEX = Regex("""^\d{2}:\d{2}-\d{2}:\d{2}$""")

        fun create(
            facilityId: String,
            date: ZonedDateTime,
            timeRange: String,
            capacity: Int,
            ownerId: Long,
        ): Slot = create(
            facilityId = facilityId,
            date = date,
            timeRange = timeRange,
            capacity = capacity,
            ownerId = ownerId,
            programId = null,
        )

        fun create(
            facilityId: String,
            date: ZonedDateTime,
            timeRange: String,
            capacity: Int,
            ownerId: Long,
            programId: Long?,
        ): Slot {
            if (!TIME_RANGE_REGEX.matches(timeRange)) {
                throw InvalidSlotException("timeRange must be HH:mm-HH:mm format, got: $timeRange")
            }
            if (capacity <= 0) {
                throw InvalidSlotException("capacity must be positive, got: $capacity")
            }
            return Slot(
                facilityId = facilityId,
                date = date,
                timeRange = timeRange,
                capacity = capacity,
                ownerId = ownerId,
                programId = programId,
                status = SlotStatus.OPEN,
            )
        }
    }
}
