package com.sportsapp.domain.booking.repository

import com.sportsapp.domain.booking.entity.Slot

interface SlotRepository {
    fun save(slot: Slot): Slot
    fun findById(id: Long): Slot?
    fun findForUpdateById(id: Long): Slot?
    fun findByFacilityId(facilityId: String): List<Slot>
    fun hasPendingOrConfirmedBooking(slotId: Long): Boolean
    fun existsActiveByFacilityId(facilityId: String): Boolean
    fun countTodayByFacilityIds(facilityIds: List<String>): Long
}
