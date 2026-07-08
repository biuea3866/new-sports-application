package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.entity.PartnerStatus

data class ChangePartnerStatusCommand(
    val partnerId: Long,
    val status: PartnerStatus,
)
