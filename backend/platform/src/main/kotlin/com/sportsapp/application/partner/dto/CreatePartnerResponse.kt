package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.entity.Partner
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.IssuedApiKey

data class CreatePartnerResponse(
    val partnerId: Long,
    val name: String,
    val status: PartnerStatus,
    val plainApiKey: String,
) {
    companion object {
        fun of(partner: Partner, issuedApiKey: IssuedApiKey): CreatePartnerResponse =
            CreatePartnerResponse(
                partnerId = requireNotNull(partner.id) { "Partner id must exist after save" },
                name = partner.name,
                status = partner.status,
                plainApiKey = issuedApiKey.plainKey,
            )
    }
}
