package com.sportsapp.presentation.payment.controller

import com.sportsapp.application.payment.usecase.CreatePaymentUseCase
import com.sportsapp.application.payment.usecase.GetPaymentUseCase
import com.sportsapp.application.payment.usecase.ListMyPaymentsUseCase
import com.sportsapp.application.payment.dto.PaymentCriteria
import com.sportsapp.application.payment.dto.PaymentResponse
import com.sportsapp.application.payment.dto.PreparePaymentResponse
import com.sportsapp.application.payment.usecase.PreparePaymentUseCase
import com.sportsapp.domain.payment.exception.MissingIdempotencyKeyException
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.payment.dto.request.CreatePaymentRequest
import com.sportsapp.presentation.payment.dto.request.PreparePaymentRequest
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.ZonedDateTime

@RestController
@RequestMapping("/payments")
@Profile("!test-jpa")
class PaymentApiController(
    private val createPaymentUseCase: CreatePaymentUseCase,
    private val preparePaymentUseCase: PreparePaymentUseCase,
    private val getPaymentUseCase: GetPaymentUseCase,
    private val listMyPaymentsUseCase: ListMyPaymentsUseCase,
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPayment(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: CreatePaymentRequest,
    ): PaymentResponse {
        if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()
        val command = request.toCommand(userId = principal.id, idempotencyKey = idempotencyKey)
        return createPaymentUseCase.execute(command)
    }

    @PostMapping("/prepare")
    @ResponseStatus(HttpStatus.CREATED)
    fun preparePayment(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key", required = false) idempotencyKey: String?,
        @Valid @RequestBody request: PreparePaymentRequest,
    ): PreparePaymentResponse {
        if (idempotencyKey.isNullOrBlank()) throw MissingIdempotencyKeyException()
        val command = request.toCommand(userId = principal.id, idempotencyKey = idempotencyKey)
        return preparePaymentUseCase.execute(command)
    }

    @GetMapping("/{id}")
    fun getPayment(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable id: Long,
    ): PaymentResponse = getPaymentUseCase.execute(userId = principal.id, paymentId = id)

    @GetMapping("/me")
    fun listMyPayments(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false) paidAtFrom: ZonedDateTime?,
        @RequestParam(required = false) paidAtTo: ZonedDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<PaymentResponse> {
        val criteria = PaymentCriteria(
            userId = principal.id,
            status = status,
            paidAtFrom = paidAtFrom,
            paidAtTo = paidAtTo,
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return listMyPaymentsUseCase.execute(criteria)
    }
}
