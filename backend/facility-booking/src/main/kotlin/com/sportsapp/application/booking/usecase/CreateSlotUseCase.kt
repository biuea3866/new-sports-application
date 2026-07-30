package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.CreateSlotCommand
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: CreateSlotCommand): Slot =
        slotDomainService.createSlot(
            ownerId = command.ownerId,
            facilityId = command.facilityId,
            date = command.date,
            timeRange = command.timeRange,
            capacity = command.capacity,
        )
}
