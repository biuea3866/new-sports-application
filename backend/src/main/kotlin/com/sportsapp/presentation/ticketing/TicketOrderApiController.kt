package com.sportsapp.presentation.ticketing

import com.sportsapp.application.ticketing.PurchaseTicketsCommand
import com.sportsapp.application.ticketing.PurchaseTicketsUseCase
import com.sportsapp.application.ticketing.TicketOrderResponse
import com.sportsapp.domain.payment.MissingIdempotencyKeyException
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ticket-orders")
class TicketOrderApiController(
    private val purchaseTicketsUseCase: PurchaseTicketsUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun purchase(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: PurchaseTicketOrderRequest,
    ): TicketOrderResponse {
        if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()
        val command = PurchaseTicketsCommand(
            userId = userId,
            lockId = request.lockId,
            idempotencyKey = idempotencyKey,
            method = request.method,
            currency = request.currency,
        )
        return purchaseTicketsUseCase.execute(command)
    }
}
