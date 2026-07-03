package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.RevokeApiKeyCommand
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RevokeApiKeyUseCase(
    private val partnerDomainService: PartnerDomainService,
) {
    @Transactional
    fun execute(command: RevokeApiKeyCommand) {
        partnerDomainService.revokeKey(command.partnerId, command.keyId)
    }
}
