package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.ReissueApiKeyCommand
import com.sportsapp.application.partner.dto.ReissueApiKeyResponse
import com.sportsapp.domain.partner.service.PartnerDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ReissueApiKeyUseCase(
    private val partnerDomainService: PartnerDomainService,
) {
    @Transactional
    fun execute(command: ReissueApiKeyCommand): ReissueApiKeyResponse {
        val issuedApiKey = partnerDomainService.reissueKey(command.partnerId)
        return ReissueApiKeyResponse.of(issuedApiKey)
    }
}
