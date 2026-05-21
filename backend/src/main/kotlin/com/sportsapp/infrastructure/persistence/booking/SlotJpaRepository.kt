package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.domain.booking.Slot
import org.springframework.data.jpa.repository.JpaRepository

interface SlotJpaRepository : JpaRepository<Slot, Long> {
    fun findByFacilityIdAndDeletedAtIsNull(facilityId: String): List<Slot>
    fun existsByFacilityIdAndDeletedAtIsNull(facilityId: String): Boolean
}
