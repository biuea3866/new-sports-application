package com.sportsapp.application.booking.usecase

import com.sportsapp.application.booking.config.BookingExpiryProperties
import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.dto.BookingExpiryFilterResult
import com.sportsapp.domain.booking.dto.BookingExpiryTtlPolicy
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * [ExpirePendingBookingsUseCase] — 6차 재설계.
 *
 * 청크마다 [ExpireBookingChunkUseCase]를 호출해 청크별 독립 트랜잭션으로 커밋한다 —
 * DomainService에는 `@Transactional`이 없다. 청크 커서(afterId)로 건너뛴 건이 다음 청크에서
 * 재조회되지 않는지도 검증한다.
 *
 * 6차 재설계 핵심: payment는 orderId별 결제 생존 판정(settled/live/attempting/none —
 * [OrderPaymentLiveness], domain.common 공유 커널)만 반환하고, 최종 만료 대상 판정
 * ([BookingDomainService.filterExpirable])은 booking 자신의 정책(빠른/느린 TTL —
 * [BookingExpiryTtlPolicy])으로 결정한다 — 이 UseCase는 두 DomainService를 호출·조합하는
 * 크로스 컨텍스트 조합만 수행한다.
 */
class ExpirePendingBookingsUseCaseTest : BehaviorSpec({

    val defaultTtlPolicy = BookingExpiryTtlPolicy(ttlMinutes = 15, readyTtlMinutes = 60)

    Given("만료 대상 PENDING 예약이 있고 만료 금지 대상이 없을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        val candidates = listOf(
            BookingExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(20)),
            BookingExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(20)),
        )
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 100) } returns candidates
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 2L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(1L, 2L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            bookingDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = listOf(1L, 2L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 2건이 결과에 반영되고 건너뛴 건수·경합 건수는 0이다") {
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 0
                result.skippedSettledCount shouldBe 0
                result.contendedCount shouldBe 0
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
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        val candidates = listOf(
            BookingExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(20)),
            BookingExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(20)),
            BookingExpiryCandidate(3L, ZonedDateTime.now().minusMinutes(20)),
        )
        val liveAnchor = ZonedDateTime.now().minusMinutes(5)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 100) } returns candidates
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 3L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(1L, 2L, 3L))
        } returns liveness
        every {
            bookingDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = listOf(1L, 3L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(1L, 3L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 금지 대상(2L)은 만료 대상에서 제외되고 건너뛴 건수로 집계된다 (오만료 방지)") {
                verify(exactly = 1) { expireBookingChunkUseCase.execute(listOf(1L, 3L)) }
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("만료 후보 중 settled(결제 완료)로 건너뛴 대상이 있을 때 (환불 판단 신호 — 별도 계측)") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        val candidates = listOf(BookingExpiryCandidate(9L, ZonedDateTime.now().minusMinutes(20)))
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(9L to OrderPaymentLiveness.Settled))
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 100) } returns candidates
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 9L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(9L))
        } returns liveness
        every {
            bookingDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(9L to OrderPaymentLiveness.Settled),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = emptyList(), skippedSettledCount = 1)
        every { expireBookingChunkUseCase.execute(emptyList()) } returns 0

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("skippedSettledCount가 별도로 1건 집계된다") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 1
                verify(exactly = 1) { expireBookingChunkUseCase.execute(emptyList()) }
            }
        }
    }

    Given("결제가 FAILED인 예약이 있을 때 (핵심 회귀 — F-A 타격)") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        // bookingId=5L의 payment는 FAILED(PG prepare 실패로 즉시 전이)라 None으로 분류된다 —
        // filterExpirable이 이를 만료 대상으로 판정해 돌려준다는 전제.
        val candidates = listOf(BookingExpiryCandidate(5L, ZonedDateTime.now().minusMinutes(20)))
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 100) } returns candidates
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 5L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(5L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            bookingDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = listOf(5L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(5L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("고립 예약(F-A)이 정확히 만료된다") {
                verify(exactly = 1) { expireBookingChunkUseCase.execute(listOf(5L)) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
            }
        }
    }

    Given("만료 대상 판정 건 중 일부가 CAS 경합에서 졌을 때 (경합 패배 건수가 계측된다)") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        // filterExpirable은 12L·13L 둘 다 만료 대상으로 판정했지만, 13L은 청크 커밋 도중
        // webhook 확정이 먼저 CONFIRMED로 전이시켜 tryExpire CAS에 진다 — execute가 1만 반환.
        val candidates = listOf(
            BookingExpiryCandidate(12L, ZonedDateTime.now().minusMinutes(20)),
            BookingExpiryCandidate(13L, ZonedDateTime.now().minusMinutes(20)),
        )
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 100) } returns candidates
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 13L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(12L, 13L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            bookingDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(12L, 13L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("expiredCount(1) + contendedCount(1) = 만료 판정 건수(2)로 경합이 별도 계측된다") {
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
                result.contendedCount shouldBe 1
            }
        }
    }

    Given("만료 대상이 0건일 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties()
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        every { bookingDomainService.findExpirableBookingCandidates(any(), any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·청크 커밋 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findPaymentLiveness(any(), any()) }
                verify(exactly = 0) { expireBookingChunkUseCase.execute(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있고 후보가 계속 남아있을 때") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(20)
        val chunk1 = listOf(BookingExpiryCandidate(10L, now), BookingExpiryCandidate(11L, now))
        val chunk2 = listOf(BookingExpiryCandidate(12L, now), BookingExpiryCandidate(13L, now))
        val chunk3 = listOf(BookingExpiryCandidate(14L, now), BookingExpiryCandidate(15L, now))

        // 매 청크가 서로 다른 id 구간을 반환해야 한다 — afterId 커서가 실제로 전진하는지 검증
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 2) } returns chunk1
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 11L, limit = 2) } returns chunk2
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 13L, limit = 2) } returns chunk3
        every { paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = any()) } returns PaymentLivenessQueryResult.empty()
        every {
            bookingDomainService.filterExpirable(candidates = chunk1, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns BookingExpiryFilterResult(expirableIds = listOf(10L, 11L), skippedSettledCount = 0)
        every {
            bookingDomainService.filterExpirable(candidates = chunk2, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns BookingExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every {
            bookingDomainService.filterExpirable(candidates = chunk3, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns BookingExpiryFilterResult(expirableIds = listOf(14L, 15L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(10L, 11L)) } returns 2
        every { expireBookingChunkUseCase.execute(listOf(12L, 13L)) } returns 2
        every { expireBookingChunkUseCase.execute(listOf(14L, 15L)) } returns 2

        When("execute를 호출하면 (후보가 계속 남아있는 상황)") {
            val result = useCase.execute()

            Then("한 주기 상한(3청크)만큼만 처리하고 종료하며, 커서가 매 청크 전진한다") {
                verify(exactly = 1) { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 2) }
                verify(exactly = 1) { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 11L, limit = 2) }
                verify(exactly = 1) { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 13L, limit = 2) }
                result.expiredCount shouldBe 6
            }
        }
    }

    Given("건너뛴(만료 금지) 건이 있는 청크 다음 청크 조회 시") {
        val bookingDomainService = mockk<BookingDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireBookingChunkUseCase = mockk<ExpireBookingChunkUseCase>()
        val properties = BookingExpiryProperties(ttlMinutes = 15, readyTtlMinutes = 60, chunkSize = 2, maxChunksPerRun = 2)
        val useCase = ExpirePendingBookingsUseCase(bookingDomainService, paymentDomainService, expireBookingChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(20)
        val chunk = listOf(BookingExpiryCandidate(20L, now), BookingExpiryCandidate(21L, now))
        val liveAnchor = ZonedDateTime.now().minusMinutes(5)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))

        // 20L은 결제 진행 중이라 건너뛰지만, 커서는 청크의 마지막 id(21L)로 전진해야 한다.
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 0L, limit = 2) } returns chunk
        every { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 21L, limit = 2) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.BOOKING, orderIds = listOf(20L, 21L))
        } returns liveness
        every {
            bookingDomainService.filterExpirable(
                candidates = chunk,
                liveness = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns BookingExpiryFilterResult(expirableIds = listOf(21L), skippedSettledCount = 0)
        every { expireBookingChunkUseCase.execute(listOf(21L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("건너뛴 건(20L)은 다음 청크 조회에서 재조회되지 않는다 (head-of-line blocking 방지)") {
                verify(exactly = 1) { bookingDomainService.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 21L, limit = 2) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 1
            }
        }
    }
})
