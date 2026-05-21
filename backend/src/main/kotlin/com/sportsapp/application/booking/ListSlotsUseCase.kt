package com.sportsapp.application.booking

import com.sportsapp.domain.booking.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListSlotsUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(facilityId: String): List<SlotResponse> =
        slotDomainService.listSlots(facilityId).map { SlotResponse.of(it) }
}
