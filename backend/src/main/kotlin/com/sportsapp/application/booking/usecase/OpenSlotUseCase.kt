package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.OpenSlotCommand
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OpenSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: OpenSlotCommand): Slot =
        slotDomainService.openSlot(command.requesterId, command.slotId)
}
