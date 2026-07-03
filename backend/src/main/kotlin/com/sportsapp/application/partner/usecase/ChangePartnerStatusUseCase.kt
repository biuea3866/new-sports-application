package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.ChangePartnerStatusCommand
import com.sportsapp.application.partner.dto.ChangePartnerStatusResponse
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ChangePartnerStatusUseCase(
    private val partnerDomainService: PartnerDomainService,
) {
    @Transactional
    fun execute(command: ChangePartnerStatusCommand): ChangePartnerStatusResponse {
        partnerDomainService.changeStatus(command.partnerId, command.status)
        return ChangePartnerStatusResponse(
            partnerId = command.partnerId,
            status = command.status,
        )
    }
}
