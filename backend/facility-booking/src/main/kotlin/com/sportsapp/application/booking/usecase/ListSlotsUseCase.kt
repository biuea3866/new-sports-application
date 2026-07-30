package com.sportsapp.application.booking.usecase

import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListSlotsUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String, programId: Long?): List<Slot> =
        slotDomainService.listSlots(facilityId, programId)
}
