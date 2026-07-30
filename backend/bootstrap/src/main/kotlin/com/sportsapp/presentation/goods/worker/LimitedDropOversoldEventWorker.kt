package com.sportsapp.presentation.goods.worker

import com.sportsapp.domain.goods.event.LimitedDropOversoldEvent
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.event.TransactionalEventListener

/**
 * 오버셀 감지 이벤트(⑥ 지능형 장애 알림 연동, BE-11) 수신 지점.
 *
 * [LimitedDropOversoldEvent.topic]이 null이라 `SpringDomainEventPublisher` 경로로 발행되며,
 * 이 리스너가 [io.micrometer.core.instrument.Counter] 지표(`limited_drop_oversell_total`)를
 * 증가시킨다 — 도메인은 Micrometer를 알지 못한다(domain 순수 유지).
 */
@Component
class LimitedDropOversoldEventWorker(
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(LimitedDropOversoldEventWorker::class.java)

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    fun onOversoldDetected(event: LimitedDropOversoldEvent) {
        log.warn(
            "LimitedDropOversoldEventWorker: oversell detected dropId={}, productId={}, detectedQuantity={}",
            event.dropId,
            event.productId,
            event.detectedQuantity,
        )
        meterRegistry.counter(OVERSELL_COUNTER).increment()
    }

    companion object {
        private const val OVERSELL_COUNTER = "limited_drop_oversell_total"
    }
}
