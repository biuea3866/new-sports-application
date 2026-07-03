package com.sportsapp.application.partner.dto

data class ChangePartnerStatusCommand(
    val partnerId: Long,
    val active: Boolean,
)
