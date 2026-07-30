package com.sportsapp.presentation.payment.controller

import com.sportsapp.application.payment.dto.ConfirmPaymentWebhookCommand
import com.sportsapp.application.payment.usecase.ConfirmPaymentWebhookUseCase
import org.springframework.context.annotation.Profile
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class PgWebhookRequest(
    val eventType: String,
    val provider: String,
    val tid: String,
    val orderId: String,
    val amount: Long,
    val status: String,
    val timestamp: String,
)

@RestController
@RequestMapping("/payments")
@Profile("!test-jpa")
class PaymentWebhookApiController(
    private val confirmPaymentWebhookUseCase: ConfirmPaymentWebhookUseCase,
) {
    @PostMapping("/webhook")
    fun receiveWebhook(@RequestBody request: PgWebhookRequest): ResponseEntity<Unit> {
        confirmPaymentWebhookUseCase.execute(
            ConfirmPaymentWebhookCommand(
                tid = request.tid,
                eventType = request.eventType,
            )
        )
        return ResponseEntity.ok().build()
    }
}
