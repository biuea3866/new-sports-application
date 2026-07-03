package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.ChangePartnerStatusCommand
import com.sportsapp.application.partner.dto.ChangePartnerStatusResponse
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangePartnerStatusUseCase(
    private val partnerDomainService: PartnerDomainService,
) {
    @Transactional
    fun execute(command: ChangePartnerStatusCommand): ChangePartnerStatusResponse {
        partnerDomainService.changeStatus(command.partnerId, command.active)
        return ChangePartnerStatusResponse(
            partnerId = command.partnerId,
            status = if (command.active) PartnerStatus.ACTIVE else PartnerStatus.SUSPENDED,
        )
    }
}
