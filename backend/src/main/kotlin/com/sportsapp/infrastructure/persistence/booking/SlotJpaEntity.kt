package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.ZonedDateTime

@Entity
@Table(name = "slots")
class SlotJpaEntity(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long,

    @Column(nullable = false)
    val facilityId: String,

    @Column(nullable = false)
    val date: ZonedDateTime,

    @Column(nullable = false)
    val timeRange: String,

    @Column(nullable = false)
    val capacity: Int,
) {
    fun toDomain(): Slot = Slot.reconstruct(
        id = id,
        facilityId = facilityId,
        date = date,
        timeRange = timeRange,
        capacity = capacity,
    )

    companion object {
        fun fromDomain(slot: Slot): SlotJpaEntity = SlotJpaEntity(
            id = slot.id,
            facilityId = slot.facilityId,
            date = slot.date,
            timeRange = slot.timeRange,
            capacity = slot.capacity,
        )
    }
}
