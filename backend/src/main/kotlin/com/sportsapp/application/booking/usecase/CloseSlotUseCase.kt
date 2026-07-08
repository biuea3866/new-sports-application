package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.CloseSlotCommand
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CloseSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: CloseSlotCommand): Slot =
        slotDomainService.closeSlot(command.requesterId, command.slotId)
}
