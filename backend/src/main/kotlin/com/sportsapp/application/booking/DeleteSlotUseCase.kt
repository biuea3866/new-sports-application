package com.sportsapp.application.booking

import com.sportsapp.domain.booking.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(requesterId: Long, slotId: Long) {
        slotDomainService.deleteSlot(requesterId, slotId)
    }
}
