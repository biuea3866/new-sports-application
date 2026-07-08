package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.dto.GenerateSlotsResult
import com.sportsapp.domain.booking.service.SlotGenerationDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GenerateSlotsUseCase(
    private val slotGenerationDomainService: SlotGenerationDomainService,
) {
    @Transactional
    fun execute(windowDays: Int): GenerateSlotsResult =
        GenerateSlotsResult(slotGenerationDomainService.generateAll(windowDays))
}
