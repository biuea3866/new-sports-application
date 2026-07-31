package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.config.GoodsOrderExpiryProperties
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryFilterResult
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryTtlPolicy
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.orm.ObjectOptimisticLockingFailureException
import java.time.ZonedDateTime

/**
 * [ExpirePendingGoodsOrdersUseCase] — W1-11a goods PENDING 주문 만료 스위퍼.
 *
 * `facility-booking`(W1-11c)의 `ExpirePendingBookingsUseCase`와 동일한 구조 — 청크마다
 * [ExpireGoodsOrderChunkUseCase]를 호출해 청크별 독립 트랜잭션으로 커밋한다. payment는
 * orderId별 결제 생존 판정(settled/live/attempting/none — [OrderPaymentLiveness],
 * domain.common 공유 커널)만 반환하고, 최종 만료 대상 판정
 * ([GoodsDomainService.filterExpirable])은 goods 자신의 정책(빠른/느린 TTL —
 * [GoodsOrderExpiryTtlPolicy])으로 결정한다. 이 UseCase는 두 DomainService를 호출·조합하는
 * 크로스 컨텍스트 조합만 수행한다.
 */
class ExpirePendingGoodsOrdersUseCaseTest : BehaviorSpec({

    val defaultTtlPolicy = GoodsOrderExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 90)

    Given("만료 대상 PENDING 주문이 있고 만료 금지 대상이 없을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val candidates = listOf(
            GoodsOrderExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(40)),
            GoodsOrderExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(40)),
        )
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 2L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(1L, 2L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            goodsDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(1L, 2L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 2건이 결과에 반영되고 건너뛴 건수·경합 건수는 0이다") {
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 0
                result.skippedSettledCount shouldBe 0
                result.contendedCount shouldBe 0
            }

            Then("청크 커밋은 ExpireGoodsOrderChunkUseCase에 위임된다") {
                verify(exactly = 1) { expireGoodsOrderChunkUseCase.execute(listOf(1L, 2L)) }
            }
        }
    }

    Given("만료 후보 중 만료 금지 대상(결제 진행 중·완료)이 섞여 있을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val candidates = listOf(
            GoodsOrderExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(40)),
            GoodsOrderExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(40)),
            GoodsOrderExpiryCandidate(3L, ZonedDateTime.now().minusMinutes(40)),
        )
        val liveAnchor = ZonedDateTime.now().minusMinutes(10)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 3L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(1L, 2L, 3L))
        } returns liveness
        every {
            goodsDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(1L, 3L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(1L, 3L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("만료 금지 대상(2L)은 만료 대상에서 제외되고 건너뛴 건수로 집계된다 (오만료 방지)") {
                verify(exactly = 1) { expireGoodsOrderChunkUseCase.execute(listOf(1L, 3L)) }
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("만료 후보 중 settled(결제 완료)로 건너뛴 대상이 있을 때 (환불 판단 신호 — 별도 계측)") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val candidates = listOf(GoodsOrderExpiryCandidate(9L, ZonedDateTime.now().minusMinutes(40)))
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(9L to OrderPaymentLiveness.Settled))
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 9L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(9L))
        } returns liveness
        every {
            goodsDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(9L to OrderPaymentLiveness.Settled),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = emptyList(), skippedSettledCount = 1)
        every { expireGoodsOrderChunkUseCase.execute(emptyList()) } returns 0

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("skippedSettledCount가 별도로 1건 집계된다") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 1
                verify(exactly = 1) { expireGoodsOrderChunkUseCase.execute(emptyList()) }
            }
        }
    }

    Given("결제가 FAILED인 주문이 있을 때 (핵심 회귀 — F-A 타격)") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        // orderId=5L의 payment는 FAILED(PG prepare 실패로 즉시 전이)라 None으로 분류된다 —
        // filterExpirable이 이를 만료 대상으로 판정해 돌려준다는 전제.
        val candidates = listOf(GoodsOrderExpiryCandidate(5L, ZonedDateTime.now().minusMinutes(40)))
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 5L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(5L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            goodsDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(5L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(5L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("고립 주문(F-A)이 정확히 만료된다") {
                verify(exactly = 1) { expireGoodsOrderChunkUseCase.execute(listOf(5L)) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
            }
        }
    }

    Given("만료 대상 판정 건 중 일부가 CAS 경합에서 졌을 때 (경합 패배 건수가 계측된다)") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        // filterExpirable은 12L·13L 둘 다 만료 대상으로 판정했지만, 13L은 청크 커밋 도중
        // webhook 확정이 먼저 CONFIRMED로 전이시켜 tryExpire CAS에 진다 — execute가 1만 반환.
        val candidates = listOf(
            GoodsOrderExpiryCandidate(12L, ZonedDateTime.now().minusMinutes(40)),
            GoodsOrderExpiryCandidate(13L, ZonedDateTime.now().minusMinutes(40)),
        )
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 13L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(12L, 13L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            goodsDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(12L, 13L)) } returns 1

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
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties()
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        every { goodsDomainService.findExpirableGoodsOrderCandidates(any(), any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·청크 커밋 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findPaymentLiveness(any(), any()) }
                verify(exactly = 0) { expireGoodsOrderChunkUseCase.execute(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있고 후보가 계속 남아있을 때") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(40)
        val chunk1 = listOf(GoodsOrderExpiryCandidate(10L, now), GoodsOrderExpiryCandidate(11L, now))
        val chunk2 = listOf(GoodsOrderExpiryCandidate(12L, now), GoodsOrderExpiryCandidate(13L, now))
        val chunk3 = listOf(GoodsOrderExpiryCandidate(14L, now), GoodsOrderExpiryCandidate(15L, now))

        // 매 청크가 서로 다른 id 구간을 반환해야 한다 — afterId 커서가 실제로 전진하는지 검증
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) } returns chunk1
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 11L, limit = 2) } returns chunk2
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 13L, limit = 2) } returns chunk3
        every { paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = any()) } returns PaymentLivenessQueryResult.empty()
        every {
            goodsDomainService.filterExpirable(candidates = chunk1, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(10L, 11L), skippedSettledCount = 0)
        every {
            goodsDomainService.filterExpirable(candidates = chunk2, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every {
            goodsDomainService.filterExpirable(candidates = chunk3, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(14L, 15L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(10L, 11L)) } returns 2
        every { expireGoodsOrderChunkUseCase.execute(listOf(12L, 13L)) } returns 2
        every { expireGoodsOrderChunkUseCase.execute(listOf(14L, 15L)) } returns 2

        When("execute를 호출하면 (후보가 계속 남아있는 상황)") {
            val result = useCase.execute()

            Then("한 주기 상한(3청크)만큼만 처리하고 종료하며, 커서가 매 청크 전진한다") {
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) }
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 11L, limit = 2) }
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 13L, limit = 2) }
                result.expiredCount shouldBe 6
            }
        }
    }

    Given("한 주기 안에서 청크 하나가 재시도 후에도 끝내 실패했을 때 (재리뷰 p2 — 청크 격리)") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(40)
        val chunk1 = listOf(GoodsOrderExpiryCandidate(30L, now), GoodsOrderExpiryCandidate(31L, now))
        val chunk2 = listOf(GoodsOrderExpiryCandidate(32L, now), GoodsOrderExpiryCandidate(33L, now))
        val chunk3 = listOf(GoodsOrderExpiryCandidate(34L, now), GoodsOrderExpiryCandidate(35L, now))

        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) } returns chunk1
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 31L, limit = 2) } returns chunk2
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 33L, limit = 2) } returns chunk3
        every { paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = any()) } returns PaymentLivenessQueryResult.empty()
        every {
            goodsDomainService.filterExpirable(candidates = chunk1, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(30L, 31L), skippedSettledCount = 0)
        every {
            goodsDomainService.filterExpirable(candidates = chunk2, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(32L, 33L), skippedSettledCount = 0)
        every {
            goodsDomainService.filterExpirable(candidates = chunk3, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(34L, 35L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(30L, 31L)) } returns 2
        // 청크2는 재시도(ExpireGoodsOrderChunkUseCase의 @Retryable) 예산을 넘어 끝내 실패한
        // 상황을 흉내낸다 — Stock(@Version) 동시 쓰기 경합의 최종 실패.
        every { expireGoodsOrderChunkUseCase.execute(listOf(32L, 33L)) } throws ObjectOptimisticLockingFailureException("stocks", null)
        every { expireGoodsOrderChunkUseCase.execute(listOf(34L, 35L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("청크2 실패가 주기 전체를 죽이지 않고 청크1·청크3이 정상 처리된다") {
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) }
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 31L, limit = 2) }
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 33L, limit = 2) }
                result.expiredCount shouldBe 4
            }

            Then("실패한 청크2의 후보 건수(2건)가 chunkFailedCount로 격리 집계된다") {
                result.chunkFailedCount shouldBe 2
            }
        }
    }

    Given("건너뛴(만료 금지) 건이 있는 청크 다음 청크 조회 시") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireGoodsOrderChunkUseCase = mockk<ExpireGoodsOrderChunkUseCase>()
        val properties = GoodsOrderExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 2, maxChunksPerRun = 2)
        val useCase = ExpirePendingGoodsOrdersUseCase(goodsDomainService, paymentDomainService, expireGoodsOrderChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(40)
        val chunk = listOf(GoodsOrderExpiryCandidate(20L, now), GoodsOrderExpiryCandidate(21L, now))
        val liveAnchor = ZonedDateTime.now().minusMinutes(10)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))

        // 20L은 결제 진행 중이라 건너뛰지만, 커서는 청크의 마지막 id(21L)로 전진해야 한다.
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) } returns chunk
        every { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 21L, limit = 2) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.GOODS, orderIds = listOf(20L, 21L))
        } returns liveness
        every {
            goodsDomainService.filterExpirable(
                candidates = chunk,
                liveness = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns GoodsOrderExpiryFilterResult(expirableIds = listOf(21L), skippedSettledCount = 0)
        every { expireGoodsOrderChunkUseCase.execute(listOf(21L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("건너뛴 건(20L)은 다음 청크 조회에서 재조회되지 않는다 (head-of-line blocking 방지)") {
                verify(exactly = 1) { goodsDomainService.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 21L, limit = 2) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 1
            }
        }
    }
})
