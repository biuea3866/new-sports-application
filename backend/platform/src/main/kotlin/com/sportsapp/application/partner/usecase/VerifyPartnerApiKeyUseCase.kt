package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyCommand
import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class VerifyPartnerApiKeyUseCase(
    private val partnerDomainService: PartnerDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(command: VerifyPartnerApiKeyCommand): VerifyPartnerApiKeyResponse =
        VerifyPartnerApiKeyResponse.of(partnerDomainService.verifyApiKey(command.plainKey))
}
