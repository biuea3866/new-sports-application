package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.service.IssuedApiKey

data class ReissueApiKeyResponse(
    val keyId: Long,
    val plainApiKey: String,
) {
    companion object {
        fun of(issuedApiKey: IssuedApiKey): ReissueApiKeyResponse =
            ReissueApiKeyResponse(
                keyId = issuedApiKey.keyId,
                plainApiKey = issuedApiKey.plainKey,
            )
    }
}
