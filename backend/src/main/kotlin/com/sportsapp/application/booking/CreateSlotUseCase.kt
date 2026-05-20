package com.sportsapp.application.booking

import com.sportsapp.domain.booking.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: CreateSlotCommand): SlotResponse {
        val slot = slotDomainService.createSlot(
            ownerId = command.ownerId,
            facilityId = command.facilityId,
            date = command.date,
            timeRange = command.timeRange,
            capacity = command.capacity,
        )
        return SlotResponse.of(slot)
    }
}
