package com.sportsapp.presentation.payment

import com.sportsapp.application.payment.CreatePaymentUseCase
import com.sportsapp.application.payment.GetPaymentUseCase
import com.sportsapp.application.payment.ListMyPaymentsUseCase
import com.sportsapp.application.payment.PaymentCriteria
import com.sportsapp.application.payment.PaymentResponse
import com.sportsapp.domain.payment.MissingIdempotencyKeyException
import com.sportsapp.domain.payment.PaymentStatus
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
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
    private val getPaymentUseCase: GetPaymentUseCase,
    private val listMyPaymentsUseCase: ListMyPaymentsUseCase,
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

    @GetMapping("/{id}")
    fun getPayment(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable id: Long,
    ): PaymentResponse = getPaymentUseCase.execute(userId = userId, paymentId = id)

    @GetMapping("/me")
    fun listMyPayments(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestParam(required = false) status: PaymentStatus?,
        @RequestParam(required = false) paidAtFrom: ZonedDateTime?,
        @RequestParam(required = false) paidAtTo: ZonedDateTime?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): Page<PaymentResponse> {
        val criteria = PaymentCriteria(
            userId = userId,
            status = status,
            paidAtFrom = paidAtFrom,
            paidAtTo = paidAtTo,
            pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
        )
        return listMyPaymentsUseCase.execute(criteria)
    }
}
