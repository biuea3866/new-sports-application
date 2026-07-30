package com.sportsapp.application.payment.dto

data class ConfirmPaymentWebhookCommand(
    val tid: String,
    val eventType: String,
)
