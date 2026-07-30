package com.sportsapp.presentation.booking.worker

import com.sportsapp.domain.booking.event.BookingRefundRequestedEvent
import com.sportsapp.domain.booking.gateway.PaymentRefundGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class BookingRefundEventWorker(
    private val paymentRefundGateway: PaymentRefundGateway,
) {
    private val log = LoggerFactory.getLogger(BookingRefundEventWorker::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRefundRequested(event: BookingRefundRequestedEvent) {
        try {
            paymentRefundGateway.requestRefund(
                paymentId = event.paymentId.toString(),
                amount = event.refundAmount,
                reason = event.reason,
            )
        } catch (exception: Exception) {
            log.error(
                "PG 환불 요청 실패 — bookingId={} paymentId={} refundAmount={} — 재시도 필요",
                event.aggregateId,
                event.paymentId,
                event.refundAmount,
                exception,
            )
        }
    }
}
