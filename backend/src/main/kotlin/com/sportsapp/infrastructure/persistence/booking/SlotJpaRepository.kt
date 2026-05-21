package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import org.springframework.data.jpa.repository.JpaRepository
import java.time.ZonedDateTime

interface SlotJpaRepository : JpaRepository<Slot, Long> {
    fun findByFacilityIdAndDeletedAtIsNull(facilityId: String): List<Slot>
    fun existsByFacilityIdAndDeletedAtIsNull(facilityId: String): Boolean
    fun countByFacilityIdInAndDeletedAtIsNullAndDateGreaterThanEqualAndDateLessThan(
        facilityIds: List<String>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long
}
