package com.sportsapp.presentation.recruitment.worker

import com.sportsapp.domain.recruitment.event.ApplicationRefundRequestedEvent
import com.sportsapp.domain.recruitment.gateway.RecruitmentRefundGateway
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

@Component
class RecruitmentRefundEventWorker(
    private val recruitmentRefundGateway: RecruitmentRefundGateway,
) {
    private val log = LoggerFactory.getLogger(RecruitmentRefundEventWorker::class.java)

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun handleRefundRequested(event: ApplicationRefundRequestedEvent) {
        val paymentId = event.paymentId ?: return
        try {
            recruitmentRefundGateway.requestRefund(paymentId, event.refundAmount, event.reason)
        } catch (exception: Exception) {
            log.error(
                "환불 요청 실패 — applicationId={} paymentId={} refundAmount={} reason={} — 재시도 필요",
                event.aggregateId,
                paymentId,
                event.refundAmount,
                event.reason,
                exception,
            )
        }
    }
}
