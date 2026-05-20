package com.sportsapp.application.booking

import com.sportsapp.domain.booking.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: UpdateSlotCommand): SlotResponse {
        val slot = slotDomainService.updateSlot(
            requesterId = command.requesterId,
            slotId = command.slotId,
            newTimeRange = command.timeRange,
            newCapacity = command.capacity,
        )
        return SlotResponse.of(slot)
    }
}
