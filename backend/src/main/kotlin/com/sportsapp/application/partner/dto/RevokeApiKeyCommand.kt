package com.sportsapp.application.partner.dto

data class RevokeApiKeyCommand(
    val partnerId: Long,
    val keyId: Long,
)
