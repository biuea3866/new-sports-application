package com.sportsapp.presentation.goods.scheduler

import com.sportsapp.application.goods.config.GoodsOrderExpiryProperties
import com.sportsapp.application.goods.usecase.ExpirePendingGoodsOrdersUseCase
import com.sportsapp.application.goods.usecase.IsGoodsOrderExpiryEnabledUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * W1-11a — goods PENDING 주문 만료 스위퍼(F-A 고립 주문 만료). C3(commerce → payment)이
 * 원격이 되면 고립 주문(재고 영구 점유) 위험이 커진다.
 *
 * [IsGoodsOrderExpiryEnabledUseCase]가 매 주기 `goods.expiry.enabled` 플래그를 런타임 조회해
 * 분기한다(no-conditional-on-property 준수 — 빈 등록 자체를 토글하지 않는다).
 * 롤백 지점: 이 플래그를 OFF로 바꾸면 재기동 없이 다음 주기부터 즉시 비활성화된다
 * (상태 전이·스키마 변경 0건).
 */
@Component
class GoodsOrderExpiryScheduler(
    private val expirePendingGoodsOrdersUseCase: ExpirePendingGoodsOrdersUseCase,
    private val isGoodsOrderExpiryEnabledUseCase: IsGoodsOrderExpiryEnabledUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(GoodsOrderExpiryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${goods.expiry.cycle-ms:${GoodsOrderExpiryProperties.DEFAULT_CYCLE_MS}}")
    fun expirePendingGoodsOrders() {
        if (!isGoodsOrderExpiryEnabledUseCase.execute()) return
        val result = expirePendingGoodsOrdersUseCase.execute()
        meterRegistry.counter(EXPIRED_COUNTER).increment(result.expiredCount.toDouble())
        meterRegistry.counter(SKIPPED_COUNTER).increment(result.skippedCount.toDouble())
        meterRegistry.counter(SKIPPED_SETTLED_COUNTER).increment(result.skippedSettledCount.toDouble())
        meterRegistry.counter(CONTENDED_COUNTER).increment(result.contendedCount.toDouble())
        log.info(
            "event=goods-order-expiry-completed expiredCount={} skippedCount={} skippedSettledCount={} contendedCount={}",
            result.expiredCount,
            result.skippedCount,
            result.skippedSettledCount,
            result.contendedCount,
        )
    }

    companion object {
        private const val EXPIRED_COUNTER = "goods_expiry_expired_total"
        private const val SKIPPED_COUNTER = "goods_expiry_skipped_total"

        /**
         * settled(결제 완료)로 건너뛴 건 전용 카운터 — 웹훅 유실·컨슈머 다운으로 "돈은
         * 받았는데 주문이 여전히 PENDING"인 환불 판단 필요 신호다. live(결제 진행 중)로
         * 건너뛴 정상 흐름과 [SKIPPED_COUNTER]에 뭉뚱그려지면 경보가 불가능해 분리했다.
         */
        private const val SKIPPED_SETTLED_COUNTER = "goods_expiry_skipped_settled_total"

        /**
         * CAS(tryExpire) 경합 전용 카운터 — 만료 대상으로 판정됐으나 청크 처리 도중 webhook
         * 확정이 먼저 CONFIRMED로 전이시켜 CAS에 진 건. 이 값이 지속적으로 0보다 크면
         * 확정·만료 경합이 잦다는 신호로, 스위퍼 주기·readyTtlMinutes 조정을 검토한다.
         */
        private const val CONTENDED_COUNTER = "goods_expiry_contended_total"
    }
}
