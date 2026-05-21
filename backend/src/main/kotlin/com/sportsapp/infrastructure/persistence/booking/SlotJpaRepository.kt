package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import java.time.ZonedDateTime
import org.springframework.data.jpa.repository.JpaRepository

interface SlotJpaRepository : JpaRepository<Slot, Long> {
    fun findByFacilityIdAndDeletedAtIsNull(facilityId: String): List<Slot>
    fun existsByFacilityIdAndDeletedAtIsNullAndDateGreaterThanEqual(
        facilityId: String,
        date: ZonedDateTime,
    ): Boolean
    fun countByFacilityIdInAndDeletedAtIsNullAndDateGreaterThanEqualAndDateLessThan(
        facilityIds: List<String>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long
}
