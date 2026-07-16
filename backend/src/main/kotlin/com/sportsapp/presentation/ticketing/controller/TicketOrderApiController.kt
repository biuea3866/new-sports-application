package com.sportsapp.presentation.ticketing.controller

import com.sportsapp.application.ticketing.usecase.GetTicketOrderUseCase
import com.sportsapp.application.ticketing.dto.PurchaseTicketsCommand
import com.sportsapp.application.ticketing.usecase.PurchaseTicketsUseCase
import com.sportsapp.application.ticketing.dto.TicketOrderDetailResponse
import com.sportsapp.application.ticketing.dto.TicketOrderResponse
import com.sportsapp.domain.payment.exception.MissingIdempotencyKeyException
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.ticketing.dto.request.PurchaseTicketOrderRequest
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
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
    private val getTicketOrderUseCase: GetTicketOrderUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun purchase(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader(value = "Idempotency-Key", required = false) idempotencyKey: String?,
        @RequestBody request: PurchaseTicketOrderRequest,
    ): TicketOrderResponse {
        if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()
        val command = PurchaseTicketsCommand(
            userId = principal.id,
            lockId = request.lockId,
            idempotencyKey = idempotencyKey,
            method = request.method,
            currency = request.currency,
        )
        return purchaseTicketsUseCase.execute(command)
    }

    @GetMapping("/{id}")
    fun getTicketOrder(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): TicketOrderDetailResponse = getTicketOrderUseCase.execute(id, principal.id)
}
