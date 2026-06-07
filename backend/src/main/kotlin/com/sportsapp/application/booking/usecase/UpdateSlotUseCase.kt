package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.UpdateSlotCommand
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: UpdateSlotCommand): Slot =
        slotDomainService.updateSlot(
            requesterId = command.requesterId,
            slotId = command.slotId,
            newTimeRange = command.timeRange,
            newCapacity = command.capacity,
        )
}
