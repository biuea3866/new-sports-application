package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.DeleteSlotCommand
import com.sportsapp.domain.booking.service.SlotDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class DeleteSlotUseCase(
    private val slotDomainService: SlotDomainService,
) {
    @Transactional
    fun execute(command: DeleteSlotCommand) {
        slotDomainService.deleteSlot(command.requesterId, command.slotId)
    }
}
