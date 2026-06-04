package com.sportsapp.domain.payment.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.PgEventType
import com.sportsapp.domain.payment.dto.ConfirmWebhookResult
import com.sportsapp.domain.payment.dto.PgInitiateCommand
import com.sportsapp.domain.payment.dto.PgInitiateResult
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.exception.NotPaymentOwnerException
import com.sportsapp.domain.payment.exception.PaymentGatewayException
import com.sportsapp.domain.payment.exception.PaymentNotFoundException
import com.sportsapp.domain.payment.gateway.OrderConfirmationGateway
import com.sportsapp.domain.payment.gateway.PaymentGateway
import com.sportsapp.domain.payment.gateway.PgPrepareRequest
import com.sportsapp.domain.payment.gateway.PgPrepareResult
import com.sportsapp.domain.payment.repository.PaymentRepository
import com.sportsapp.domain.payment.vo.OrderType
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.payment.vo.toPgProviderName
import java.math.BigDecimal
import org.slf4j.LoggerFactory
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

@Service
class PaymentDomainService(
    private val paymentRepository: PaymentRepository,
    private val paymentGateway: PaymentGateway,
    private val orderConfirmationGateway: OrderConfirmationGateway,
    private val domainEventPublisher: DomainEventPublisher,
    private val transactionTemplate: TransactionTemplate,
) {
    companion object {
        private val log = LoggerFactory.getLogger(PaymentDomainService::class.java)
    }

    /**
     * 1단계: 멱등 키 확인 후 PENDING Payment 를 DB 에 저장한다.
     * @Transactional 을 통해 커밋 후 paymentId 를 반환한다 (Entity 반환 금지).
     */
    @Transactional
    fun createPending(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Long {
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing.id

        val payment = Payment.create(
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
        )
        return paymentRepository.save(payment).id
    }

    /**
     * 2단계: @Transactional 경계 밖에서 PG 를 호출한 뒤, TransactionTemplate 으로
     * 새 트랜잭션을 열어 markReady / markFailed 를 DB 에 반영한다.
     * PG 실패 시 Payment 를 FAILED 로 전이하고 예외를 전파하지 않는다.
     */
    fun initiatePg(command: PgInitiateCommand): PgInitiateResult {
        val pgResult = callPgOrNull(command)
        return transactionTemplate.execute { applyPgResult(command.paymentId, pgResult) }
            ?: throw PaymentNotFoundException(command.paymentId)
    }

    private fun callPgOrNull(command: PgInitiateCommand): PgPrepareResult? = try {
        paymentGateway.prepare(
            PgPrepareRequest(
                provider = command.method.toPgProviderName(),
                idempotencyKey = command.idempotencyKey,
                userId = command.userId,
                orderType = command.orderType,
                orderId = command.orderId,
                amount = command.amount,
                currency = command.currency,
                itemName = command.itemName,
                returnUrl = command.returnUrl,
                failUrl = command.failUrl,
            )
        )
    } catch (exception: PaymentGatewayException) {
        log.warn("PG prepare failed paymentId={} reason={}", command.paymentId, exception.message)
        null
    }

    private fun applyPgResult(paymentId: Long, pgResult: PgPrepareResult?): PgInitiateResult {
        val payment = paymentRepository.findById(paymentId) ?: throw PaymentNotFoundException(paymentId)
        if (payment.status == PaymentStatus.PENDING) {
            when (pgResult) {
                null -> payment.markFailed("PG prepare failed")
                else -> payment.markReady(tid = pgResult.tid, provider = pgResult.provider, checkoutUrl = pgResult.checkoutUrl)
            }
            paymentRepository.save(payment)
        }
        return PgInitiateResult(
            paymentId = payment.id,
            status = payment.status,
            pgTransactionId = payment.pgTransactionId,
            checkoutUrl = payment.checkoutUrl,
        )
    }

    fun prepare(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
        itemName: String,
        returnUrl: String,
        failUrl: String,
    ): Payment {
        val existing = paymentRepository.findByIdempotencyKey(idempotencyKey)
        if (existing != null) return existing

        val payment = Payment.create(
            userId = userId,
            idempotencyKey = idempotencyKey,
            orderType = orderType,
            orderId = orderId,
            method = method,
            amount = amount,
            currency = currency,
        )

        val provider = method.toPgProviderName()
        val prepareResult = paymentGateway.prepare(
            PgPrepareRequest(
                provider = provider,
                idempotencyKey = idempotencyKey,
                userId = userId,
                orderType = orderType,
                orderId = orderId,
                amount = amount,
                currency = currency,
                itemName = itemName,
                returnUrl = returnUrl,
                failUrl = failUrl,
            )
        )

        payment.markReady(
            tid = prepareResult.tid,
            provider = prepareResult.provider,
            checkoutUrl = prepareResult.checkoutUrl,
        )
        return paymentRepository.save(payment)
    }

    fun create(
        userId: Long,
        idempotencyKey: String,
        orderType: OrderType,
        orderId: Long,
        method: PaymentMethod,
        amount: BigDecimal,
        currency: String,
    ): Payment = prepare(
        userId = userId,
        idempotencyKey = idempotencyKey,
        orderType = orderType,
        orderId = orderId,
        method = method,
        amount = amount,
        currency = currency,
        itemName = "$orderType #$orderId",
        returnUrl = "",
        failUrl = "",
    )

    @Transactional
    fun confirmWebhook(tid: String, eventType: String): ConfirmWebhookResult {
        val payment = paymentRepository.findByPgTransactionId(tid)
            ?: throw PaymentNotFoundException(-1L)

        return when (PgEventType.fromValueOrNull(eventType)) {
            PgEventType.PAYMENT_APPROVED -> {
                if (payment.status == PaymentStatus.COMPLETED) return ConfirmWebhookResult.of(payment)
                payment.markCompleted(ZonedDateTime.now())
                val saved = paymentRepository.save(payment)
                orderConfirmationGateway.confirm(
                    orderType = saved.orderType,
                    orderId = saved.orderId,
                    paymentId = saved.id,
                )
                domainEventPublisher.publishAll(saved.pullDomainEvents())
                ConfirmWebhookResult.of(saved)
            }
            PgEventType.PAYMENT_CANCELED -> {
                if (payment.status == PaymentStatus.CANCELLED) return ConfirmWebhookResult.of(payment)
                payment.markCancelled()
                val saved = paymentRepository.save(payment)
                orderConfirmationGateway.cancel(
                    orderType = saved.orderType,
                    orderId = saved.orderId,
                    paymentId = saved.id,
                )
                ConfirmWebhookResult.of(saved)
            }
            else -> {
                log.warn("confirmWebhook: 미인식 eventType={} tid={}", eventType, tid)
                ConfirmWebhookResult.of(payment)
            }
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun findByPgTransactionIdOrThrow(pgTransactionId: String): ConfirmWebhookResult {
        val payment = paymentRepository.findByPgTransactionId(pgTransactionId)
            ?: throw PaymentNotFoundException(-1L)
        return ConfirmWebhookResult.of(payment)
    }

    fun findStatuses(paymentIds: List<Long>): Map<Long, PaymentStatus> {
        if (paymentIds.isEmpty()) return emptyMap()
        return paymentRepository.findAllByIdIn(paymentIds)
            .associate { it.id to it.status }
    }

    fun getPayment(userId: Long, paymentId: Long): Payment {
        val payment = paymentRepository.findById(paymentId)
            ?: throw PaymentNotFoundException(paymentId)
        if (payment.userId != userId) throw NotPaymentOwnerException(paymentId)
        return payment
    }

    fun findMyPayments(
        userId: Long,
        status: PaymentStatus?,
        paidAtFrom: ZonedDateTime?,
        paidAtTo: ZonedDateTime?,
        pageable: Pageable,
    ): Page<Payment> = paymentRepository.findByUserIdAndConditions(
        userId = userId,
        status = status,
        paidAtFrom = paidAtFrom,
        paidAtTo = paidAtTo,
        pageable = pageable,
    )
}
