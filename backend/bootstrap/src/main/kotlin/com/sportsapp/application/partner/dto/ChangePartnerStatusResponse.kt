package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.entity.PartnerStatus

data class ChangePartnerStatusResponse(
    val partnerId: Long,
    val status: PartnerStatus,
)
