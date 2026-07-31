package com.sportsapp.presentation.partner.dto.request

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyCommand
import jakarta.validation.constraints.NotBlank

data class VerifyPartnerApiKeyRequest(
    @field:NotBlank
    val apiKey: String,
) {
    fun toCommand(): VerifyPartnerApiKeyCommand = VerifyPartnerApiKeyCommand(plainKey = apiKey)
}
