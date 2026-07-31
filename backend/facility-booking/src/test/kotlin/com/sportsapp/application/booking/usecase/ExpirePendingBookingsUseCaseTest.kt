package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.application.payment.config.PaymentExpiryGuardProperties
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
 *
 * 재확정(재리뷰): findUnexpirableOrderIds 3번째 인자(activeWindowMinutes)는
 * [PaymentExpiryGuardProperties]에서 온다 — status만으로 판단하던 구 로직은 결제 개시 시점에
 * 이미 생성되는 PENDING/READY 행 때문에 모든 예약이 만료 금지로 걸려 스위퍼가 무력화됐다.
 * "방치된 READY가 있어도 TTL이 지난 예약은 만료된다"(핵심 회귀 케이스)는 payment 측
 * 판정(findUnexpirableOrderIds가 emptySet 반환)을 이 UseCase가 그대로 신뢰해 만료 처리로
 * 이어지는지로 검증한다 — 판정 로직 자체의 exhaustive 검증은 PaymentExpiryGuardTest 참고.
 */
class ExpirePendingBookingsUseCaseTest : BehaviorSpec({

    val paymentExpiryGuardProperties = PaymentExpiryGuardProperties(activeWindowMinutes = 5)

    Given("만료 대상 PENDING 예약이 있고 만료 금지 대상이 없을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        every { bookingDomainService.findExpirableBookingIds(15, 0L, 100) } returns listOf(1L, 2L)
        every { bookingDomainService.findExpirableBookingIds(15, 2L, 100) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L), 5L) } returns emptySet()
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
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        every { bookingDomainService.findExpirableBookingIds(15, 0L, 100) } returns listOf(1L, 2L, 3L)
        every { bookingDomainService.findExpirableBookingIds(15, 3L, 100) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(1L, 2L, 3L), 5L) } returns setOf(2L)
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

    Given("주문 생성 시 만들어진 뒤 방치된 READY payment가 있어도 (핵심 회귀 케이스)") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        // bookingId=5L의 payment는 READY지만 updatedAt이 활동 창(5분)보다 오래돼 방치된 것으로
        // 판정된다 — payment 측이 이를 정확히 만료 허용(emptySet)으로 판정해 돌려준다는 전제.
        // status만 보던 구 로직(findCompletedOrderIds -> findUnexpirableOrderIds 1차 확장)은
        // 이 경우도 항상 만료 금지로 판정해 스위퍼를 완전히 무력화시켰다.
        every { bookingDomainService.findExpirableBookingIds(15, 0L, 100) } returns listOf(5L)
        every { bookingDomainService.findExpirableBookingIds(15, 5L, 100) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(5L), 5L) } returns emptySet()
        every { expireBookingChunkUseCase.execute(listOf(5L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("TTL이 지난 예약은 만료된다 — 방치된 READY payment 존재가 만료를 막지 않는다") {
                verify(exactly = 1) { expireBookingChunkUseCase.execute(listOf(5L)) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
            }
        }
    }

    Given("만료 대상이 0건일 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties()
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        every { bookingDomainService.findExpirableBookingIds(any(), any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·청크 커밋 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findUnexpirableOrderIds(any(), any(), any()) }
                verify(exactly = 0) { expireBookingChunkUseCase.execute(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있고 후보가 계속 남아있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        // 매 청크가 서로 다른 id 구간을 반환해야 한다 — afterId 커서가 실제로 전진하는지 검증
        every { bookingDomainService.findExpirableBookingIds(15, 0L, 2) } returns listOf(10L, 11L)
        every { bookingDomainService.findExpirableBookingIds(15, 11L, 2) } returns listOf(12L, 13L)
        every { bookingDomainService.findExpirableBookingIds(15, 13L, 2) } returns listOf(14L, 15L)
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, any(), 5L) } returns emptySet()
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
        val useCase = ExpirePendingBookingsUseCase(
            bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties, paymentExpiryGuardProperties,
        )

        // 20L은 결제 진행 중이라 건너뛰지만, 커서는 청크의 마지막 id(21L)로 전진해야 한다.
        every { bookingDomainService.findExpirableBookingIds(15, 0L, 2) } returns listOf(20L, 21L)
        every { bookingDomainService.findExpirableBookingIds(15, 21L, 2) } returns emptyList()
        every { paymentDomainService.findUnexpirableOrderIds(OrderType.BOOKING, listOf(20L, 21L), 5L) } returns setOf(20L)
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
