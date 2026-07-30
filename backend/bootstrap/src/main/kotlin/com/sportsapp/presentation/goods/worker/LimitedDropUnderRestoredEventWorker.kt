package com.sportsapp.presentation.goods.worker

import com.sportsapp.domain.goods.event.LimitedDropUnderRestoredEvent
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 언더셀 복원 감지 이벤트(FIX-04, Observability) 수신 지점.
 *
 * [LimitedDropUnderRestoredEvent.topic]이 null이라 `SpringDomainEventPublisher` 경로로 발행되며,
 * 이 리스너가 [io.micrometer.core.instrument.Counter] 지표 2종을 증가시킨다 — 도메인은 Micrometer를
 * 알지 못한다(domain 순수 유지). [LimitedDropOversoldEventWorker]와 동일한 패턴을 따른다.
 */
@Component
class LimitedDropUnderRestoredEventWorker(
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(LimitedDropUnderRestoredEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onUnderRestored(event: LimitedDropUnderRestoredEvent) {
        log.warn(
            "LimitedDropUnderRestoredEventWorker: under-sell restored dropId={}, productId={}, restoredCount={}, staleReservationCount={}",
            event.dropId,
            event.productId,
            event.restoredCount,
            event.staleReservationCount,
        )
        meterRegistry.counter(UNDER_RESTORED_COUNTER).increment(event.restoredCount.toDouble())
        meterRegistry.counter(STALE_RESERVATION_COUNTER).increment(event.staleReservationCount.toDouble())
    }

    companion object {
        private const val UNDER_RESTORED_COUNTER = "limited_drop_underrestored_total"
        private const val STALE_RESERVATION_COUNTER = "limited_drop_underrestored_stale_total"
    }
}
