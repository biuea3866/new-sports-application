package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * [ExpirePendingBookingsUseCase]는 청크마다 [ExpireBookingChunkUseCase]를 호출해 청크별
 * 독립 트랜잭션으로 커밋한다(리뷰 ③) — DomainService에는 더 이상 `@Transactional`이 없다.
 *
 * 청크 커서(afterId)로 건너뛴 건이 다음 청크에서 재조회되지 않는지도 검증한다(리뷰 ④).
 */
class ExpirePendingBookingsUseCaseTest : BehaviorSpec({

    Given("만료 대상 PENDING 예약이 있고 만료 금지 대상이 없을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        every { bookingDomainService.findExpirableBookingIds(15, 0L, 100) } returns listOf(1L, 2L)
        every { bookingDomainService.findExpirableBookingIds(15, 2L, 100) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L)) } returns emptySet()
        every { expireBookingChunkUseCase.execute(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 2건이 결과에 반영되고 건너뛴 건수는 0이다") {
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 0
            }

            Then("청크 커밋은 ExpireBookingChunkUseCase에 위임된다") {
                verify(exactly = 1) { expireBookingChunkUseCase.execute(listOf(1L, 2L)) }
            }
        }
    }

    Given("만료 후보 중 만료 금지 대상(결제 진행 중·완료)이 섞여 있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        every { bookingDomainService.findExpirableBookingIds(15, 0L, 100) } returns listOf(1L, 2L, 3L)
        every { bookingDomainService.findExpirableBookingIds(15, 3L, 100) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L)) } returns setOf(2L)
        every { expireBookingChunkUseCase.execute(listOf(1L, 3L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 금지 대상(2L)은 만료 대상에서 제외되고 건너뛴 건수로 집계된다 (오만료 방지)") {
                verify(exactly = 1) { expireBookingChunkUseCase.execute(listOf(1L, 3L)) }
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 1
            }
        }
    }

    Given("만료 대상이 0건일 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties()
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        every { bookingDomainService.findExpirableBookingIds(any(), any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·청크 커밋 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findUnexpirableOrderIds(any(), any()) }
                verify(exactly = 0) { expireBookingChunkUseCase.execute(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있고 후보가 계속 남아있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        // 매 청크가 서로 다른 id 구간을 반환해야 한다 — afterId 커서가 실제로 전진하는지 검증
        every { bookingDomainService.findExpirableBookingIds(15, 0L, 2) } returns listOf(10L, 11L)
        every { bookingDomainService.findExpirableBookingIds(15, 11L, 2) } returns listOf(12L, 13L)
        every { bookingDomainService.findExpirableBookingIds(15, 13L, 2) } returns listOf(14L, 15L)
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, any()) } returns emptySet()
        every { expireBookingChunkUseCase.execute(listOf(10L, 11L)) } returns 2
        every { expireBookingChunkUseCase.execute(listOf(12L, 13L)) } returns 2
        every { expireBookingChunkUseCase.execute(listOf(14L, 15L)) } returns 2

        When("execute를 호출하면 (후보가 계속 남아있는 상황)") {
            val result = useCase.execute()

            Then("한 주기 상한(3청크)만큼만 처리하고 종료하며, 커서가 매 청크 전진한다") {
                verify(exactly = 1) { bookingDomainService.findExpirableBookingIds(15, 0L, 2) }
                verify(exactly = 1) { bookingDomainService.findExpirableBookingIds(15, 11L, 2) }
                verify(exactly = 1) { bookingDomainService.findExpirableBookingIds(15, 13L, 2) }
                result.expiredCount shouldBe 6
            }
        }
    }

    Given("건너뛴(만료 금지) 건이 있는 청크 다음 청크 조회 시") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(chunkSize = 2, maxChunksPerRun = 2)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        // 20L은 결제 진행 중이라 건너뛰지만, 커서는 청크의 마지막 id(21L)로 전진해야 한다.
        every { bookingDomainService.findExpirableBookingIds(15, 0L, 2) } returns listOf(20L, 21L)
        every { bookingDomainService.findExpirableBookingIds(15, 21L, 2) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(20L, 21L)) } returns setOf(20L)
        every { expireBookingChunkUseCase.execute(listOf(21L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("건너뛴 건(20L)은 다음 청크 조회에서 재조회되지 않는다 (head-of-line blocking 방지)") {
                verify(exactly = 1) { bookingDomainService.findExpirableBookingIds(15, 21L, 2) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 1
            }
        }
    }
})
