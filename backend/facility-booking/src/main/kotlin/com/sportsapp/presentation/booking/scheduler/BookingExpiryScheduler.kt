package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.usecase.ExpirePendingBookingsUseCase
import com.sportsapp.application.booking.usecase.IsBookingExpiryEnabledUseCase
import io.micrometer.core.instrument.MeterRegistry
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼(F-A 고립 예약 만료). C1(booking → payment)이
 * 원격이 되면 고립 예약(슬롯 영구 점유) 위험이 커진다.
 *
 * [IsBookingExpiryEnabledUseCase]가 매 주기 `booking.expiry.enabled` 플래그를 런타임 조회해
 * 분기한다(no-conditional-on-property 준수 — 빈 등록 자체를 토글하지 않는다).
 * 롤백 지점: 이 플래그를 OFF로 바꾸면 재기동 없이 다음 주기부터 즉시 비활성화된다
 * (상태 전이·스키마 변경 0건).
 */
@Component
class BookingExpiryScheduler(
    private val expirePendingBookingsUseCase: ExpirePendingBookingsUseCase,
    private val isBookingExpiryEnabledUseCase: IsBookingExpiryEnabledUseCase,
    private val meterRegistry: MeterRegistry,
) {
    private val log = LoggerFactory.getLogger(BookingExpiryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${booking.expiry.cycle-ms:${BookingExpiryProperties.DEFAULT_CYCLE_MS}}")
    fun expirePendingBookings() {
        if (!isBookingExpiryEnabledUseCase.execute()) return
        val result = expirePendingBookingsUseCase.execute()
        meterRegistry.counter(EXPIRED_COUNTER).increment(result.expiredCount.toDouble())
        meterRegistry.counter(SKIPPED_COUNTER).increment(result.skippedCount.toDouble())
        meterRegistry.counter(SKIPPED_SETTLED_COUNTER).increment(result.skippedSettledCount.toDouble())
        log.info(
            "event=booking-expiry-completed expiredCount={} skippedCount={} skippedSettledCount={}",
            result.expiredCount,
            result.skippedCount,
            result.skippedSettledCount,
        )
    }

    companion object {
        private const val EXPIRED_COUNTER = "booking_expiry_expired_total"
        private const val SKIPPED_COUNTER = "booking_expiry_skipped_total"

        /**
         * settled(결제 완료)로 건너뛴 건 전용 카운터 — 웹훅 유실·컨슈머 다운으로 "돈은
         * 받았는데 예약이 여전히 PENDING"인 환불 판단 필요 신호다. live(결제 진행 중)로
         * 건너뛴 정상 흐름과 [SKIPPED_COUNTER]에 뭉뚱그려지면 경보가 불가능해 분리했다.
         */
        private const val SKIPPED_SETTLED_COUNTER = "booking_expiry_skipped_settled_total"
    }
}
