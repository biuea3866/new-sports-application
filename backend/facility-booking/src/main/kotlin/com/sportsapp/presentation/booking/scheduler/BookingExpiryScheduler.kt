package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.usecase.ExpirePendingBookingsUseCase
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼(F-A 고립 예약 만료). C1(booking → payment)이
 * 원격이 되면 고립 예약(슬롯 영구 점유) 위험이 커진다.
 *
 * `booking.expiry.enabled=false`(env 재정의 가능, 기본 true)면 빈은 등록되지만 아무 것도
 * 하지 않는다 — no-conditional-on-property 준수(빈 등록 자체를 토글하지 않고 런타임 조회).
 * 롤백 지점: 이 플래그를 false로 두면 즉시 비활성화된다(상태 전이·스키마 변경 0건).
 */
@Component
class BookingExpiryScheduler(
    private val expirePendingBookingsUseCase: ExpirePendingBookingsUseCase,
    private val bookingExpiryProperties: BookingExpiryProperties,
) {
    private val log = LoggerFactory.getLogger(BookingExpiryScheduler::class.java)

    @Scheduled(fixedDelayString = "\${booking.expiry.cycle-ms:300000}")
    fun expirePendingBookings() {
        if (!bookingExpiryProperties.enabled) return
        val result = expirePendingBookingsUseCase.execute()
        log.info(
            "event=booking-expiry-completed expiredCount={} skippedCount={}",
            result.expiredCount,
            result.skippedCount,
        )
    }
}
