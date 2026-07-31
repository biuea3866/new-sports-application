package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime

class ExpirePendingBookingsUseCaseTest : BehaviorSpec({

    Given("만료 대상 PENDING 예약이 있고 결제 성공 건이 없을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val properties = BookingExpiryProperties(enabled = true, ttlMinutes = 15, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, properties)

        val thresholdSlot = slot<ZonedDateTime>()
        every { bookingDomainService.findExpirableBookingIds(capture(thresholdSlot), 100) } returnsMany
            listOf(listOf(1L, 2L), emptyList())
        every { paymentDomainService.findCompletedOrderIds(OrderType.BOOKING, listOf(1L, 2L)) } returns emptySet()
        every { bookingDomainService.expireBookings(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("TTL 임계값이 now - 15분 근방으로 계산되어 조회된다 (경계값 계산)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(15)).abs().seconds
                (diff < 5) shouldBe true
            }

            Then("만료 2건이 결과에 반영되고 건너뛴 건수는 0이다") {
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 0
            }
        }
    }

    Given("만료 후보 중 결제 성공 건이 섞여 있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val properties = BookingExpiryProperties(chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, properties)

        every { bookingDomainService.findExpirableBookingIds(any(), 100) } returnsMany
            listOf(listOf(1L, 2L, 3L), emptyList())
        every { paymentDomainService.findCompletedOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L)) } returns setOf(2L)
        every { bookingDomainService.expireBookings(listOf(1L, 3L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 성공 건(2L)은 만료 대상에서 제외되고 건너뛴 건수로 집계된다 (오만료 방지)") {
                verify(exactly = 1) { bookingDomainService.expireBookings(listOf(1L, 3L)) }
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 1
            }
        }
    }

    Given("만료 대상이 0건일 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val properties = BookingExpiryProperties()
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, properties)

        every { bookingDomainService.findExpirableBookingIds(any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·만료 처리 없이 쓰기 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findCompletedOrderIds(any(), any()) }
                verify(exactly = 0) { bookingDomainService.expireBookings(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val properties = BookingExpiryProperties(chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, properties)

        every { bookingDomainService.findExpirableBookingIds(any(), 2) } returns listOf(10L, 11L)
        every { paymentDomainService.findCompletedOrderIds(OrderType.BOOKING, listOf(10L, 11L)) } returns emptySet()
        every { bookingDomainService.expireBookings(listOf(10L, 11L)) } returns 2

        When("execute를 호출하면 (후보가 계속 남아있는 상황)") {
            val result = useCase.execute()

            Then("한 주기 상한(3청크)만큼만 처리하고 종료한다") {
                verify(exactly = 3) { bookingDomainService.findExpirableBookingIds(any(), 2) }
                result.expiredCount shouldBe 6
            }
        }
    }
})
