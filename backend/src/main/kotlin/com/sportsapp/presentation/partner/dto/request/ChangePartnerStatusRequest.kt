package com.sportsapp.presentation.partner.dto.request

import com.sportsapp.application.partner.dto.ChangePartnerStatusCommand
import com.sportsapp.domain.partner.entity.PartnerStatus
import jakarta.validation.constraints.Pattern

data class ChangePartnerStatusRequest(
    @field:Pattern(regexp = "ACTIVE|SUSPENDED", message = "status must be ACTIVE or SUSPENDED")
    val status: String,
) {
    fun toCommand(partnerId: Long): ChangePartnerStatusCommand = ChangePartnerStatusCommand(
        partnerId = partnerId,
        status = PartnerStatus.valueOf(status),
    )
}
