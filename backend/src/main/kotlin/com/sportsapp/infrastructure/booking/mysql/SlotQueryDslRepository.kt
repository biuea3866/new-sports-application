package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.domain.booking.entity.Slot

interface SlotQueryDslRepository {
    fun findByFacilityId(facilityId: String, programId: Long?): List<Slot>
}
