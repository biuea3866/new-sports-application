package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.application.booking.usecase.ExpirePendingBookingsUseCase
import com.sportsapp.application.booking.usecase.IsBookingExpiryEnabledUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * W1-11c — booking.expiry.enabled 런타임 플래그 분기(no-conditional-on-property 준수).
 * 빈 등록 자체는 항상 되고, 실행 시점에 [IsBookingExpiryEnabledUseCase]로 플래그를 조회해
 * 분기한다. 지표 카운터(booking_expiry_expired_total/booking_expiry_skipped_total/
 * booking_expiry_skipped_settled_total)도 검증한다.
 */
class BookingExpirySchedulerTest : BehaviorSpec({

    Given("booking.expiry.enabled=true(플래그 ON)") {
        val useCase = mockk<ExpirePendingBookingsUseCase>()
        val isEnabledUseCase = mockk<IsBookingExpiryEnabledUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = BookingExpiryScheduler(useCase, isEnabledUseCase, meterRegistry)
        every { isEnabledUseCase.execute() } returns true
        every { useCase.execute() } returns BookingExpiryResult(
            expiredCount = 3,
            skippedCount = 2,
            skippedSettledCount = 1,
            contendedCount = 1,
        )

        When("expirePendingBookings를 호출하면") {
            scheduler.expirePendingBookings()

            Then("ExpirePendingBookingsUseCase를 1회 호출한다") {
                verify(exactly = 1) { useCase.execute() }
            }

            Then("만료·건너뜀·settled 건너뜀·경합 건수가 각각 카운터에 반영된다 (환불 판단 신호·경합 분리 계측)") {
                meterRegistry.counter("booking_expiry_expired_total").count() shouldBe 3.0
                meterRegistry.counter("booking_expiry_skipped_total").count() shouldBe 2.0
                meterRegistry.counter("booking_expiry_skipped_settled_total").count() shouldBe 1.0
                meterRegistry.counter("booking_expiry_contended_total").count() shouldBe 1.0
            }
        }
    }

    Given("booking.expiry.enabled=false(플래그 OFF, 롤백 경로)") {
        val useCase = mockk<ExpirePendingBookingsUseCase>()
        val isEnabledUseCase = mockk<IsBookingExpiryEnabledUseCase>()
        val meterRegistry = SimpleMeterRegistry()
        val scheduler = BookingExpiryScheduler(useCase, isEnabledUseCase, meterRegistry)
        every { isEnabledUseCase.execute() } returns false

        When("expirePendingBookings를 호출하면") {
            scheduler.expirePendingBookings()

            Then("ExpirePendingBookingsUseCase를 호출하지 않고 아무 것도 하지 않는다") {
                verify(exactly = 0) { useCase.execute() }
            }
        }
    }
})
