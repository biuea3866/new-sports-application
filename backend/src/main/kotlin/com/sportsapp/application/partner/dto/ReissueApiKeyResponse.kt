package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.service.IssuedApiKey

data class ReissueApiKeyResponse(
    val keyId: Long,
    val plainApiKey: String,
) {
    companion object {
        fun of(issuedApiKey: IssuedApiKey): ReissueApiKeyResponse =
            ReissueApiKeyResponse(
                keyId = requireNotNull(issuedApiKey.apiKey.id) { "PartnerApiKey id must exist after save" },
                plainApiKey = issuedApiKey.plainKey,
            )
    }
}
