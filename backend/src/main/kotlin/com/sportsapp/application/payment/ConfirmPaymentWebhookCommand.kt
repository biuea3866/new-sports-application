package com.sportsapp.application.payment

data class ConfirmPaymentWebhookCommand(
    val tid: String,
    val eventType: String,
)
