package com.sportsapp.presentation.ticketing.scheduler

import com.sportsapp.application.ticketing.config.TicketOrderExpiryProperties
import com.sportsapp.application.ticketing.usecase.ExpirePendingTicketOrdersUseCase
import com.sportsapp.application.ticketing.usecase.IsTicketOrderExpiryEnabledUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * W1-11b — ticketing PENDING 주문 만료 스위퍼(F-A 고립 주문 만료). C4(ticketing → payment)가
 * 원격이 되면 고립 주문(좌석 재판매 불가·이중 발권 창) 위험이 커진다. booking(W1-11c)의
 * [com.sportsapp.presentation.booking.scheduler.BookingExpiryScheduler]와 동일한 구조.
 *
 * [IsTicketOrderExpiryEnabledUseCase]가 매 주기 `ticketing.expiry.enabled` 플래그를 런타임
 * 조회해 분기한다(no-conditional-on-property 준수 — 빈 등록 자체를 토글하지 않는다).
 * 롤백 지점: 이 플래그를 OFF로 바꾸면 재기동 없이 다음 주기부터 즉시 비활성화된다(상태
 * 전이·스키마 변경 0건).
 */
@Component
class TicketOrderExpiryScheduler(
    private val expirePendingTicketOrdersUseCase: ExpirePendingTicketOrdersUseCase,
    private val isTicketOrderExpiryEnabledUseCase: IsTicketOrderExpiryEnabledUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(TicketOrderExpiryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${ticketing.expiry.cycle-ms:${TicketOrderExpiryProperties.DEFAULT_CYCLE_MS}}")
    fun expirePendingTicketOrders() {
        if (!isTicketOrderExpiryEnabledUseCase.execute()) return
        val result = expirePendingTicketOrdersUseCase.execute()
        meterRegistry.counter(EXPIRED_COUNTER).increment(result.expiredCount.toDouble())
        meterRegistry.counter(SKIPPED_COUNTER).increment(result.skippedCount.toDouble())
        meterRegistry.counter(SKIPPED_SETTLED_COUNTER).increment(result.skippedSettledCount.toDouble())
        meterRegistry.counter(CONTENDED_COUNTER).increment(result.contendedCount.toDouble())
        log.info(
            "event=ticketing-expiry-completed expiredCount={} skippedCount={} skippedSettledCount={} contendedCount={}",
            result.expiredCount,
            result.skippedCount,
            result.skippedSettledCount,
            result.contendedCount,
        )
    }

    companion object {
        private const val EXPIRED_COUNTER = "ticketing_expiry_expired_total"
        private const val SKIPPED_COUNTER = "ticketing_expiry_skipped_total"

        /**
         * settled(결제 완료)로 건너뛴 건 전용 카운터 — 웹훅 유실·컨슈머 다운으로 "돈은
         * 받았는데 주문이 여전히 PENDING"인 환불 판단 필요 신호다.
         */
        private const val SKIPPED_SETTLED_COUNTER = "ticketing_expiry_skipped_settled_total"

        /**
         * CAS(tryExpire) 경합 전용 카운터 — 만료 대상으로 판정됐으나 청크 처리 도중 webhook
         * 확정이 먼저 CONFIRMED로 전이시켜 CAS에 진 건.
         */
        private const val CONTENDED_COUNTER = "ticketing_expiry_contended_total"
    }
}
