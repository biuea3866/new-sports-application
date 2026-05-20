package com.sportsapp.presentation.payment

import com.sportsapp.application.payment.CreatePaymentUseCase
import com.sportsapp.application.payment.PaymentResponse
import com.sportsapp.domain.payment.MissingIdempotencyKeyException
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/payments")
@Profile("!test-jpa")
class PaymentApiController(
    private val createPaymentUseCase: CreatePaymentUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreatePaymentRequest,
    ): PaymentResponse {
        if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()
        val command = request.toCommand(userId = 1L, idempotencyKey = idempotencyKey)
        return createPaymentUseCase.execute(command)
    }
}
