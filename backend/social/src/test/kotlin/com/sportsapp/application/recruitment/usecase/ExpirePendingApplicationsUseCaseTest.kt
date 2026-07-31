package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.config.RecruitmentApplicationExpiryProperties
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessQueryResult
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryFilterResult
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryTtlPolicy
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * [ExpirePendingApplicationsUseCase] — W1-11d.
 *
 * 청크마다 [ExpireApplicationChunkUseCase]를 호출해 청크별 독립 트랜잭션으로 커밋한다 —
 * DomainService에는 `@Transactional`이 없다. 청크 커서(afterId)로 건너뛴 건이 다음 청크에서
 * 재조회되지 않는지도 검증한다.
 *
 * payment는 orderId별 결제 생존 판정(settled/live/attempting/none — [OrderPaymentLiveness],
 * domain.common 공유 커널)만 반환하고, 최종 취소 대상 판정
 * ([RecruitmentDomainService.filterExpirable])은 recruitment 자신의 정책(빠른/느린 TTL —
 * [ApplicationExpiryTtlPolicy])으로 결정한다 — 이 UseCase는 두 DomainService를 호출·조합하는
 * 크로스 컨텍스트 조합만 수행한다.
 */
class ExpirePendingApplicationsUseCaseTest : BehaviorSpec({

    val defaultTtlPolicy = ApplicationExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 90)

    Given("취소 대상 PENDING 신청이 있고 취소 금지 대상이 없을 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val candidates = listOf(
            ApplicationExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(40)),
            ApplicationExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(40)),
        )
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 2L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(1L, 2L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            recruitmentDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(1L, 2L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("취소 2건이 결과에 반영되고 건너뛴 건수·경합 건수는 0이다") {
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 0
                result.skippedSettledCount shouldBe 0
                result.contendedCount shouldBe 0
            }

            Then("청크 커밋은 ExpireApplicationChunkUseCase에 위임된다") {
                verify(exactly = 1) { expireApplicationChunkUseCase.execute(listOf(1L, 2L)) }
            }
        }
    }

    Given("취소 후보 중 취소 금지 대상(결제 진행 중)이 섞여 있을 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val candidates = listOf(
            ApplicationExpiryCandidate(1L, ZonedDateTime.now().minusMinutes(40)),
            ApplicationExpiryCandidate(2L, ZonedDateTime.now().minusMinutes(40)),
            ApplicationExpiryCandidate(3L, ZonedDateTime.now().minusMinutes(40)),
        )
        val liveAnchor = ZonedDateTime.now().minusMinutes(10)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 3L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(1L, 2L, 3L))
        } returns liveness
        every {
            recruitmentDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(1L, 3L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(1L, 3L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("취소 금지 대상(2L)은 취소 대상에서 제외되고 건너뛴 건수로 집계된다 (오취소 방지)") {
                verify(exactly = 1) { expireApplicationChunkUseCase.execute(listOf(1L, 3L)) }
                result.expiredCount shouldBe 2
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("취소 후보 중 settled(결제 완료)로 건너뛴 대상이 있을 때 (환불 판단 신호 — 별도 계측)") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val candidates = listOf(ApplicationExpiryCandidate(9L, ZonedDateTime.now().minusMinutes(40)))
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(9L to OrderPaymentLiveness.Settled))
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 9L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(9L))
        } returns liveness
        every {
            recruitmentDomainService.filterExpirable(
                candidates = candidates,
                liveness = mapOf(9L to OrderPaymentLiveness.Settled),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = emptyList(), skippedSettledCount = 1)
        every { expireApplicationChunkUseCase.execute(emptyList()) } returns 0

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("skippedSettledCount가 별도로 1건 집계된다") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 1
                result.skippedSettledCount shouldBe 1
                verify(exactly = 1) { expireApplicationChunkUseCase.execute(emptyList()) }
            }
        }
    }

    Given("결제가 FAILED인 신청이 있을 때 (핵심 회귀 — F-A 타격)") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        // applicationId=5L의 payment는 FAILED(PG prepare 실패로 즉시 전이)라 None으로 분류된다 —
        // filterExpirable이 이를 취소 대상으로 판정해 돌려준다는 전제.
        val candidates = listOf(ApplicationExpiryCandidate(5L, ZonedDateTime.now().minusMinutes(40)))
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 5L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(5L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            recruitmentDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(5L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(5L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("고립 신청(F-A)이 정확히 취소된다") {
                verify(exactly = 1) { expireApplicationChunkUseCase.execute(listOf(5L)) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
            }
        }
    }

    Given("취소 대상 판정 건 중 일부가 CAS 경합에서 졌을 때 (경합 패배 건수가 계측된다)") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 100, maxChunksPerRun = 20)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val candidates = listOf(
            ApplicationExpiryCandidate(12L, ZonedDateTime.now().minusMinutes(40)),
            ApplicationExpiryCandidate(13L, ZonedDateTime.now().minusMinutes(40)),
        )
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 100) } returns candidates
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 13L, limit = 100) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(12L, 13L))
        } returns PaymentLivenessQueryResult.empty()
        every {
            recruitmentDomainService.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(12L, 13L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("expiredCount(1) + contendedCount(1) = 취소 판정 건수(2)로 경합이 별도 계측된다") {
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 0
                result.contendedCount shouldBe 1
            }
        }
    }

    Given("취소 대상이 0건일 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties()
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        every { recruitmentDomainService.findExpirableApplicationCandidates(any(), any(), any()) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("결제 조회·청크 커밋 없이 종료한다 (엣지)") {
                result.expiredCount shouldBe 0
                result.skippedCount shouldBe 0
                verify(exactly = 0) { paymentDomainService.findPaymentLiveness(any(), any()) }
                verify(exactly = 0) { expireApplicationChunkUseCase.execute(any()) }
            }
        }
    }

    Given("한 주기 최대 청크 수(maxChunksPerRun)가 설정되어 있고 후보가 계속 남아있을 때") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 2, maxChunksPerRun = 3)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(40)
        val chunk1 = listOf(ApplicationExpiryCandidate(10L, now), ApplicationExpiryCandidate(11L, now))
        val chunk2 = listOf(ApplicationExpiryCandidate(12L, now), ApplicationExpiryCandidate(13L, now))
        val chunk3 = listOf(ApplicationExpiryCandidate(14L, now), ApplicationExpiryCandidate(15L, now))

        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) } returns chunk1
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 11L, limit = 2) } returns chunk2
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 13L, limit = 2) } returns chunk3
        every { paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = any()) } returns PaymentLivenessQueryResult.empty()
        every {
            recruitmentDomainService.filterExpirable(candidates = chunk1, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(10L, 11L), skippedSettledCount = 0)
        every {
            recruitmentDomainService.filterExpirable(candidates = chunk2, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(12L, 13L), skippedSettledCount = 0)
        every {
            recruitmentDomainService.filterExpirable(candidates = chunk3, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(14L, 15L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(10L, 11L)) } returns 2
        every { expireApplicationChunkUseCase.execute(listOf(12L, 13L)) } returns 2
        every { expireApplicationChunkUseCase.execute(listOf(14L, 15L)) } returns 2

        When("execute를 호출하면 (후보가 계속 남아있는 상황)") {
            val result = useCase.execute()

            Then("한 주기 상한(3청크)만큼만 처리하고 종료하며, 커서가 매 청크 전진한다") {
                verify(exactly = 1) { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) }
                verify(exactly = 1) { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 11L, limit = 2) }
                verify(exactly = 1) { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 13L, limit = 2) }
                result.expiredCount shouldBe 6
            }
        }
    }

    Given("건너뛴(취소 금지) 건이 있는 청크 다음 청크 조회 시") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val paymentDomainService = mockk<PaymentDomainService>()
        val expireApplicationChunkUseCase = mockk<ExpireApplicationChunkUseCase>()
        val properties = RecruitmentApplicationExpiryProperties(ttlMinutes = 30, readyTtlMinutes = 90, chunkSize = 2, maxChunksPerRun = 2)
        val useCase = ExpirePendingApplicationsUseCase(recruitmentDomainService, paymentDomainService, expireApplicationChunkUseCase, properties)

        val now = ZonedDateTime.now().minusMinutes(40)
        val chunk = listOf(ApplicationExpiryCandidate(20L, now), ApplicationExpiryCandidate(21L, now))
        val liveAnchor = ZonedDateTime.now().minusMinutes(10)
        val liveness = PaymentLivenessQueryResult(livenessByOrderId = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)))

        // 20L은 결제 진행 중이라 건너뛰지만, 커서는 청크의 마지막 id(21L)로 전진해야 한다.
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 0L, limit = 2) } returns chunk
        every { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 21L, limit = 2) } returns emptyList()
        every {
            paymentDomainService.findPaymentLiveness(orderType = OrderType.RECRUITMENT, orderIds = listOf(20L, 21L))
        } returns liveness
        every {
            recruitmentDomainService.filterExpirable(
                candidates = chunk,
                liveness = mapOf(20L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )
        } returns ApplicationExpiryFilterResult(expirableIds = listOf(21L), skippedSettledCount = 0)
        every { expireApplicationChunkUseCase.execute(listOf(21L)) } returns 1

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("건너뛴 건(20L)은 다음 청크 조회에서 재조회되지 않는다 (head-of-line blocking 방지)") {
                verify(exactly = 1) { recruitmentDomainService.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 21L, limit = 2) }
                result.expiredCount shouldBe 1
                result.skippedCount shouldBe 1
            }
        }
    }
})
