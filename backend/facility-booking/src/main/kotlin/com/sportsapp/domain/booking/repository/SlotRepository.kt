package com.sportsapp.domain.booking.repository

import com.sportsapp.domain.booking.entity.Slot

interface SlotRepository {
    fun save(slot: Slot): Slot
    fun findById(id: Long): Slot?

    /**
     * 여러 예약의 Slot을 한 번에 조회한다(목록 조회의 N+1 방지).
     * 삭제·부재한 Slot은 결과에서 빠지므로 호출부가 방어 처리한다.
     */
    fun findAllByIds(ids: List<Long>): List<Slot>
    fun findForUpdateById(id: Long): Slot?
    fun findByFacilityId(facilityId: String, programId: Long?): List<Slot>
    fun hasPendingOrConfirmedBooking(slotId: Long): Boolean
    fun existsActiveByFacilityId(facilityId: String): Boolean
    fun countTodayByFacilityIds(facilityIds: List<String>): Long
}
