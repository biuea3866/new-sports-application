package com.sportsapp.domain.recruitment.service

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentLivenessClassifier
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryCandidate
import com.sportsapp.domain.recruitment.dto.ApplicationExpiryTtlPolicy
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.entity.Recruitment
import com.sportsapp.domain.recruitment.entity.RecruitmentStatus
import com.sportsapp.domain.recruitment.policy.CancellationPolicy
import com.sportsapp.domain.recruitment.repository.ApplicationCustomRepository
import com.sportsapp.domain.recruitment.repository.ApplicationRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentCustomRepository
import com.sportsapp.domain.recruitment.repository.RecruitmentRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

/**
 * W1-11d — recruitment PENDING 신청 만료 스위퍼가 사용하는 [RecruitmentDomainService]의
 * 후보 조회(findExpirableApplicationCandidates)·최종 판정(filterExpirable)·만료 전이
 * (expireApplications — 정원 복원 포함)·킬 스위치(isExpiryEnabled)를 검증한다.
 *
 * payment 판정(settled/live/attempting/none)의 세부 단조성 불변식은 domain.common의
 * `OrderPaymentLivenessTest`가 이미 전수 검증한다 — 이 테스트는 그 판정을 recruitment 자신의
 * 정책(두 TTL — [ApplicationExpiryTtlPolicy])으로 소비하는 지점만 검증한다. "mock 스텁에
 * 결론을 박지 말라"는 티켓 경고에 따라 FAILED/READY 시나리오는 [PaymentLivenessClassifier]를
 * 실제로 호출해(raw [PaymentLivenessRow] 입력) 산출한 값을 그대로 소비한다.
 */
class RecruitmentExpiryDomainServiceTest : BehaviorSpec({

    fun buildService(
        applicationRepository: ApplicationRepository = mockk(),
        recruitmentRepository: RecruitmentRepository = mockk(),
        featureFlagEvaluator: FeatureFlagEvaluator = mockk(),
    ): RecruitmentDomainService = RecruitmentDomainService(
        recruitmentRepository = recruitmentRepository,
        applicationRepository = applicationRepository,
        distributedLock = mockk<DistributedLock>(relaxed = true),
        cancellationPolicy = mockk<CancellationPolicy>(),
        domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true),
        recruitmentCustomRepository = mockk<RecruitmentCustomRepository>(),
        applicationCustomRepository = mockk<ApplicationCustomRepository>(),
        featureFlagEvaluator = featureFlagEvaluator,
    )

    val defaultTtlPolicy = ApplicationExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 90)

    fun recruitmentOf(capacity: Int): Recruitment = Recruitment.create(
        title = "주말 축구 모임",
        capacity = capacity,
        feeAmount = BigDecimal("10000"),
        activityAt = ZonedDateTime.now().plusDays(10),
        applicationDeadline = ZonedDateTime.now().plusDays(5),
        communityId = null,
        recruiterUserId = 1L,
    )

    Given("TTL 분·커서·조회 상한이 주어졌을 때") {
        val applicationRepository = mockk<ApplicationRepository>()
        val service = buildService(applicationRepository = applicationRepository)
        val thresholdSlot = slot<ZonedDateTime>()
        val candidates = listOf(
            ApplicationExpiryCandidate(applicationId = 10L, createdAt = ZonedDateTime.now().minusMinutes(40)),
            ApplicationExpiryCandidate(applicationId = 11L, createdAt = ZonedDateTime.now().minusMinutes(31)),
        )
        every { applicationRepository.findPendingCreatedBefore(capture(thresholdSlot), 5L, 100) } returns candidates

        When("findExpirableApplicationCandidates를 호출하면") {
            val result = service.findExpirableApplicationCandidates(ttlMinutes = 30, afterId = 5L, limit = 100)

            Then("ApplicationRepository 조회 결과를 그대로 반환한다") {
                result shouldBe candidates
            }

            Then("TTL 임계값이 now - 30분 근방으로 이 메서드 내부에서 계산된다 (no-time-parameter)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(30)).abs().seconds
                (diff < 5) shouldBe true
            }
        }
    }

    Given("결제가 settled(완료)인 후보가 있을 때 (절대 취소 금지)") {
        val service = buildService()
        val candidates = listOf(
            ApplicationExpiryCandidate(applicationId = 1L, createdAt = ZonedDateTime.now().minusDays(1)),
        )

        When("filterExpirable을 호출하면 (Settled에 포함, 앵커가 아무리 오래돼도)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(1L to OrderPaymentLiveness.Settled),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에서 제외되고 settled 건너뜀으로 집계된다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 1
            }
        }
    }

    Given("TTL이 지나지 않은 PENDING 신청일 때 (경계값 — 아직 취소하지 않는다)") {
        val service = buildService()
        val candidates = listOf(
            ApplicationExpiryCandidate(applicationId = 2L, createdAt = ZonedDateTime.now().minusMinutes(29)),
        )

        When("filterExpirable을 호출하면 (liveness=None, 빠른 TTL 30분 미도달)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.None),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상이 아니다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 FAILED인 30분 경과 신청일 때 (F-A 타격 — 핵심 회귀)") {
        val service = buildService()
        val createdAt = ZonedDateTime.now().minusMinutes(31)
        val candidates = listOf(ApplicationExpiryCandidate(applicationId = 3L, createdAt = createdAt))
        // PG prepare 실패로 즉시 FAILED로 전이된 payment 행 — PaymentLivenessClassifier가 None으로 분류한다.
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 3L, status = PaymentStatus.FAILED, createdAt = createdAt)),
        )

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(3L to liveness.of(3L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("고립 신청(F-A)이 정확히 만료 대상으로 판정된다") {
                result.expirableIds shouldBe listOf(3L)
            }
        }
    }

    Given("결제가 CANCELLED·REFUNDED이거나 payment 행이 없을 때 (빠른 TTL로 취소)") {
        val service = buildService()
        val createdAt = ZonedDateTime.now().minusMinutes(31)
        val cancelledLiveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 4L, status = PaymentStatus.CANCELLED, createdAt = createdAt)),
        )
        val refundedLiveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 5L, status = PaymentStatus.REFUNDED, createdAt = createdAt)),
        )
        val candidates = listOf(
            ApplicationExpiryCandidate(applicationId = 4L, createdAt = createdAt),
            ApplicationExpiryCandidate(applicationId = 5L, createdAt = createdAt),
            ApplicationExpiryCandidate(applicationId = 6L, createdAt = createdAt),
        )

        When("filterExpirable을 호출하면 (6L은 liveness 맵에 아예 없음 = payment 행 없음)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(4L to cancelledLiveness.of(4L), 5L to refundedLiveness.of(5L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("세 건 모두 빠른 TTL로 만료 대상이다") {
                result.expirableIds.toSet() shouldBe setOf(4L, 5L, 6L)
            }
        }
    }

    Given("결제가 READY이고 발급 40분 경과일 때 (느린 TTL 90분 미도달 — 오취소 방지)") {
        val service = buildService()
        val readyAt = ZonedDateTime.now().minusMinutes(40)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 7L, status = PaymentStatus.READY, createdAt = readyAt)),
        )
        val candidates = listOf(ApplicationExpiryCandidate(applicationId = 7L, createdAt = readyAt))

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(7L to liveness.of(7L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("결제창에 머무는 사용자로 보아 취소되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 READY이고 발급 100분 경과일 때 (느린 TTL 90분 초과 — 무한 점유 방지)") {
        val service = buildService()
        val readyAt = ZonedDateTime.now().minusMinutes(100)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 8L, status = PaymentStatus.READY, createdAt = readyAt)),
        )
        val candidates = listOf(ApplicationExpiryCandidate(applicationId = 8L, createdAt = readyAt))

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(8L to liveness.of(8L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("느린 TTL도 지나 취소 대상이다") {
                result.expirableIds shouldBe listOf(8L)
            }
        }
    }

    Given("주문은 오래됐지만 방금 재결제를 시도해 payment 행이 PENDING일 때 (Attempting — 오만료 금지)") {
        val service = buildService()
        val applicationCreatedAt = ZonedDateTime.now().minusMinutes(60)
        val attemptStartedAt = ZonedDateTime.now().minusMinutes(10)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 9L, status = PaymentStatus.PENDING, createdAt = attemptStartedAt)),
        )
        val candidates = listOf(ApplicationExpiryCandidate(applicationId = 9L, createdAt = applicationCreatedAt))

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(9L to liveness.of(9L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("빠른 TTL 앵커가 주문 생성 시각이 아니라 시도 시각과의 최댓값이므로 취소되지 않는다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("PG 왕복 대기 중인 PENDING payment 행이 빠른 TTL을 지났을 때 (Attempting — 무한 점유 방지)") {
        val service = buildService()
        val applicationCreatedAt = ZonedDateTime.now().minusMinutes(60)
        val attemptStartedAt = ZonedDateTime.now().minusMinutes(40)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 10L, status = PaymentStatus.PENDING, createdAt = attemptStartedAt)),
        )
        val candidates = listOf(ApplicationExpiryCandidate(applicationId = 10L, createdAt = applicationCreatedAt))

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(10L to liveness.of(10L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("시도 시각도 빠른 TTL 30분을 넘겼으므로 취소 대상이다") {
                result.expirableIds shouldBe listOf(10L)
            }
        }
    }

    Given("readyTtlMinutes가 ttlMinutes 이하일 때 (설정 불변조건 위반)") {
        Then("ApplicationExpiryTtlPolicy 생성이 IllegalArgumentException으로 실패한다") {
            io.kotest.assertions.throwables.shouldThrow<IllegalArgumentException> {
                ApplicationExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 30)
            }
        }
    }

    Given("만료 후보가 비어있을 때") {
        val service = buildService()

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(candidates = emptyList(), liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)

            Then("빈 목록을 반환한다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("TTL이 지난 PENDING 신청이 있고, 만료 후 정원에 여유가 생겨 CLOSED였던 모집이 다시 신청 가능해질 때") {
        val applicationRepository = mockk<ApplicationRepository>()
        val recruitmentRepository = mockk<RecruitmentRepository>()
        val service = buildService(applicationRepository = applicationRepository, recruitmentRepository = recruitmentRepository)

        val application = Application.create(recruitmentId = 100L, applicantUserId = 1L)
        val recruitment = recruitmentOf(capacity = 3).apply { closeWhenFull(currentApplicantCount = 3) }
        every { applicationRepository.tryExpire(9L) } returns true
        every { applicationRepository.findById(9L) } returns application
        every { recruitmentRepository.findById(100L) } returns recruitment
        every { applicationRepository.countActiveByRecruitmentId(100L) } returns 2
        every { recruitmentRepository.save(any()) } answers { firstArg() }

        When("expireApplications를 호출하면") {
            val expiredCount = service.expireApplications(listOf(9L))

            Then("CAS 조건부 UPDATE로 전이되고 모집이 CLOSED에서 OPEN으로 재전이된다") {
                expiredCount shouldBe 1
                recruitment.status shouldBe RecruitmentStatus.OPEN
                verify(exactly = 1) { recruitmentRepository.save(recruitment) }
            }
        }
    }

    Given("이미 PENDING이 아닌(CONFIRMED 등) 신청에") {
        val applicationRepository = mockk<ApplicationRepository>()
        val service = buildService(applicationRepository = applicationRepository)
        every { applicationRepository.tryExpire(20L) } returns false

        When("expireApplications를 호출하면") {
            val expiredCount = service.expireApplications(listOf(20L))

            Then("CAS 조건(WHERE status=PENDING)에 걸리지 않아 영향 행 0건으로 멱등하게 처리되고 정원 복원도 시도하지 않는다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { applicationRepository.findById(any()) }
            }
        }
    }

    Given("만료 대상 id 목록이 비어있을 때") {
        val applicationRepository = mockk<ApplicationRepository>()
        val service = buildService(applicationRepository = applicationRepository)

        When("expireApplications를 호출하면") {
            val expiredCount = service.expireApplications(emptyList())

            Then("조회·CAS 쓰기 없이 0을 반환한다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { applicationRepository.tryExpire(any()) }
            }
        }
    }

    Given("recruitment.expiry.enabled 플래그가 true일 때") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("recruitment.expiry.enabled", FeatureContext.anonymous(), true)
        } returns true

        When("isExpiryEnabled를 호출하면") {
            Then("true를 반환한다") {
                service.isExpiryEnabled() shouldBe true
            }
        }
    }

    Given("recruitment.expiry.enabled 플래그가 false일 때(운영 킬 스위치)") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("recruitment.expiry.enabled", FeatureContext.anonymous(), true)
        } returns false

        When("isExpiryEnabled를 호출하면") {
            Then("false를 반환한다 (재기동 없이 즉시 반영)") {
                service.isExpiryEnabled() shouldBe false
            }
        }
    }
})
