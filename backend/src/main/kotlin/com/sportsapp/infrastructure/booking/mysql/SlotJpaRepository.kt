package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.entity.Slot
import jakarta.persistence.LockModeType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import java.time.ZonedDateTime

interface SlotJpaRepository : JpaRepository<Slot, Long>, SlotQueryDslRepository {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    fun findForUpdateById(id: Long): Slot?
    fun existsByFacilityIdAndDeletedAtIsNull(facilityId: String): Boolean
    fun countByFacilityIdInAndDeletedAtIsNullAndDateGreaterThanEqualAndDateLessThan(
        facilityIds: List<String>,
        from: ZonedDateTime,
        to: ZonedDateTime,
    ): Long
}
