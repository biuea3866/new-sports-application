package com.sportsapp.presentation.booking.scheduler

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.booking.dto.BookingExpiryResult
import com.sportsapp.application.booking.usecase.ExpirePendingBookingsUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * W1-11c — booking.expiry.enabled 런타임 플래그 분기(no-conditional-on-property 준수).
 * 빈 등록 자체는 항상 되고, 실행 시점에 플래그를 조회해 분기한다.
 */
class BookingExpirySchedulerTest : BehaviorSpec({

    Given("booking.expiry.enabled=true 설정") {
        val useCase = mockk<ExpirePendingBookingsUseCase>()
        val properties = BookingExpiryProperties(enabled = true)
        val scheduler = BookingExpiryScheduler(useCase, properties)
        every { useCase.execute() } returns BookingExpiryResult(expiredCount = 3, skippedCount = 1)

        When("expirePendingBookings를 호출하면") {
            scheduler.expirePendingBookings()

            Then("ExpirePendingBookingsUseCase를 1회 호출한다") {
                verify(exactly = 1) { useCase.execute() }
            }
        }
    }

    Given("booking.expiry.enabled=false 설정 (롤백 경로)") {
        val useCase = mockk<ExpirePendingBookingsUseCase>()
        val properties = BookingExpiryProperties(enabled = false)
        val scheduler = BookingExpiryScheduler(useCase, properties)

        When("expirePendingBookings를 호출하면") {
            scheduler.expirePendingBookings()

            Then("ExpirePendingBookingsUseCase를 호출하지 않고 아무 것도 하지 않는다") {
                verify(exactly = 0) { useCase.execute() }
            }
        }
    }
})
