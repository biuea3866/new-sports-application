package com.sportsapp.presentation.partner.dto.request

import com.sportsapp.application.partner.dto.CreatePartnerCommand
import jakarta.validation.constraints.NotBlank

data class CreatePartnerRequest(
    @field:NotBlank
    val name: String,
) {
    fun toCommand(): CreatePartnerCommand = CreatePartnerCommand(name = name)
}
