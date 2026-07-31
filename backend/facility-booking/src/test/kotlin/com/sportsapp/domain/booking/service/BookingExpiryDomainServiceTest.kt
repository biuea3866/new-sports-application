package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.dto.BookingExpiryCandidate
import com.sportsapp.domain.booking.dto.BookingExpiryTtlPolicy
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentLivenessClassifier
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼가 사용하는 [BookingDomainService]의
 * 후보 조회(findExpirableBookingCandidates)·최종 판정(filterExpirable)·만료 전이
 * (expireBookings)·킬 스위치(isExpiryEnabled)를 검증한다.
 *
 * **6차 재설계**: payment의 판정을 sealed 타입([OrderPaymentLiveness])으로 받아 최종 만료
 * 대상을 가리는 로직([filterExpirable])을 이 DomainService가 소유한다. 크로스 컨텍스트
 * 조합은 application(ExpirePendingBookingsUseCase)이 담당하지만, "이 후보들 중 실제로
 * 만료시킬 대상"을 고르는 booking 자신의 정책(빠른/느린 TTL)은 domain에 남는다 —
 * [OrderPaymentLiveness]는 domain.common 공유 커널이라 booking domain이 직접 import해도
 * 도메인 교차가 아니다.
 *
 * 슬롯 점유 해제는 별도 보상 로직이 아니라, PENDING → EXPIRED 전이 자체로 완료된다 —
 * 슬롯 점유는 countBySlotIdAndStatusIn(PENDING, CONFIRMED)로 파생되므로 EXPIRED로 전이되면
 * 그 즉시 활성 카운트에서 제외된다.
 *
 * 만료 전이는 [BookingRepository.tryExpire] CAS(조건부 UPDATE, WHERE status='PENDING')로
 * 수행한다 — 청크 트랜잭션이 REPEATABLE READ 스냅샷을 뜬 이후 다른 트랜잭션이 커밋한 CONFIRMED를
 * EXPIRED로 덮어쓰는 lost update를 막는다. find→mutate→save 경로를 쓰지 않으므로
 * 이 테스트는 BookingRepository의 tryExpire 반환값(affected rows > 0)만으로 검증한다.
 */
class BookingExpiryDomainServiceTest : BehaviorSpec({

    fun buildService(
        bookingRepository: BookingRepository,
        featureFlagEvaluator: FeatureFlagEvaluator = mockk(),
    ): BookingDomainService = BookingDomainService(
        bookingRepository = bookingRepository,
        slotRepository = mockk<SlotRepository>(),
        distributedLock = mockk<DistributedLock>(relaxed = true),
        domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true),
        bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>(),
        featureFlagEvaluator = featureFlagEvaluator,
    )

    val defaultTtlPolicy = BookingExpiryTtlPolicy(ttlMinutes = 15, readyTtlMinutes = 60)

    Given("TTL 분·커서·조회 상한이 주어졌을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val thresholdSlot = slot<ZonedDateTime>()
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 10L, createdAt = ZonedDateTime.now().minusMinutes(20)),
            BookingExpiryCandidate(bookingId = 11L, createdAt = ZonedDateTime.now().minusMinutes(16)),
        )
        every { bookingRepository.findPendingCreatedBefore(capture(thresholdSlot), 5L, 100) } returns candidates

        When("findExpirableBookingCandidates를 호출하면") {
            val result = service.findExpirableBookingCandidates(ttlMinutes = 15, afterId = 5L, limit = 100)

            Then("BookingRepository 조회 결과를 그대로 반환한다") {
                result shouldBe candidates
            }

            Then("TTL 임계값이 now - 15분 근방으로 이 메서드 내부에서 계산된다 (no-time-parameter)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(15)).abs().seconds
                (diff < 5) shouldBe true
            }
        }
    }

    Given("결제가 settled(완료)인 후보가 있을 때 (절대 만료 금지)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 1L, createdAt = ZonedDateTime.now().minusMinutes(100)),
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

    Given("결제가 live(READY)이고 발급 시각이 readyTtl을 아직 지나지 않았을 때 (오만료 방지 — 핵심 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 2L, createdAt = ZonedDateTime.now().minusMinutes(20)),
        )

        When("filterExpirable을 호출하면 (readyTtlMinutes=60, payment 발급 시각은 20분 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(ZonedDateTime.now().minusMinutes(20))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("결제창에 머무는 사용자로 보아 만료 대상에서 제외된다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("결제가 live(READY)이지만 발급 시각이 readyTtl을 지났을 때 (무한 점유 방지 — 핵심 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 3L, createdAt = ZonedDateTime.now().minusMinutes(70)),
        )

        When("filterExpirable을 호출하면 (readyTtlMinutes=60, payment 발급 시각은 70분 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(3L to OrderPaymentLiveness.Live(ZonedDateTime.now().minusMinutes(70))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("느린 TTL도 지나 만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(3L)
            }
        }
    }

    Given("결제 시도 이력이 없거나 전부 종결(None)일 때 (F-A 타격 — 핵심 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 4L, createdAt = ZonedDateTime.now().minusMinutes(16)),
        )

        When("filterExpirable을 호출하면 (liveness가 None, readyTtlMinutes=60 미도달이어도)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(4L to OrderPaymentLiveness.None),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("빠른 TTL(예약 생성 시각 기준)로 이미 만료 대상이다") {
                result.expirableIds shouldBe listOf(4L)
            }
        }
    }

    Given("liveness 맵에 아예 없는(=None과 동치) 후보일 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 40L, createdAt = ZonedDateTime.now().minusMinutes(16)),
        )

        When("filterExpirable을 호출하면 (liveness 맵에 해당 bookingId 자체가 없음)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("None과 동일하게 빠른 TTL로 만료 대상이다") {
                result.expirableIds shouldBe listOf(40L)
            }
        }
    }

    Given("70분 전 생성된 예약에 방금 새 READY payment가 생겼을 때 (5차 재설계 핵심 회귀 — 앵커 이전으로 인한 오만료 재발 방지)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        // 예약(booking.createdAt)은 70분 전에 생성됐지만, 방금(1분 전) 새 payment가
        // READY로 발급됐다 — 4차는 booking.createdAt만 보고 오만료시켰다.
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 6L, createdAt = ZonedDateTime.now().minusMinutes(70)),
        )

        When("filterExpirable을 호출하면 (readyTtlMinutes=60, payment 발급 시각은 1분 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(6L to OrderPaymentLiveness.Live(ZonedDateTime.now().minusMinutes(1))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("예약 생성 시각과 무관하게 payment 발급 시각 기준으로 만료되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("한 주문에 payment 2행(오래된 READY + 최근 READY)이 liveness 최댓값으로 이미 집계돼 들어올 때 (최댓값 앵커 반영 확인)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 7L, createdAt = ZonedDateTime.now().minusDays(3)),
        )

        When("filterExpirable을 호출하면 (liveness는 PaymentLivenessClassifier가 이미 최댓값으로 계산해 넘긴 값 — 5분 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(7L to OrderPaymentLiveness.Live(ZonedDateTime.now().minusMinutes(5))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("최근 발급 시각 기준으로 아직 readyTtl을 지나지 않아 만료되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("70분 전 생성된 예약에 방금(재결제 시도 중) 새 PENDING payment가 삽입됐을 때 (6차 재리뷰 p1 — 핵심 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        // booking.createdAt은 70분 전(ttlMinutes=15을 이미 지남)이지만, 방금(5초 전) 재결제
        // 시도로 새 PENDING payment 행이 삽입됐다 — 5차는 이 경우를 None으로 오판정해
        // 즉시 만료를 허용했다(재현 시나리오: createPending 커밋 직후 ~ PG 응답 사이 스위퍼가 돎).
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 100L, createdAt = ZonedDateTime.now().minusMinutes(70)),
        )

        When("filterExpirable을 호출하면 (liveness=Attempting, since=5초 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(100L to OrderPaymentLiveness.Attempting(ZonedDateTime.now().minusSeconds(5))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("예약 생성 시각과 무관하게 재결제 시도 시각 기준으로 만료되지 않는다 — max(createdAt, attemptSince) 앵커") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("60분 지난 READY 예약에 방금 재결제 시도가 삽입됐을 때 (7차 재리뷰 p1 — 카테고리 우선순위 결함 종단 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        // orderId=102L의 payment: 65분 전 발급된 READY(readyTtlMinutes=60을 이미 초과) +
        // 5초 전 삽입된 재결제 PENDING. 7차 이전(카테고리 우선순위) 로직이면 attempting을
        // 아예 보지 않고 오래된 READY만으로 Live(65분 전)를 반환해 즉시 오만료됐다 —
        // PaymentLivenessClassifier.classify()를 실제로 호출해 그 산출물이 filterExpirable에서
        // 어떻게 소비되는지 종단으로 검증한다(스텁에 결론을 미리 박지 않는다).
        val staleReadyAt = ZonedDateTime.now().minusMinutes(65)
        val retryAttemptAt = ZonedDateTime.now().minusSeconds(5)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(
                PaymentLivenessRow(orderId = 102L, status = PaymentStatus.READY, createdAt = staleReadyAt),
                PaymentLivenessRow(orderId = 102L, status = PaymentStatus.PENDING, createdAt = retryAttemptAt),
            ),
        )
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 102L, createdAt = staleReadyAt),
        )

        When("classify 결과(Attempting이어야 한다)를 filterExpirable에 그대로 넘기면") {
            liveness.of(102L).shouldBeInstanceOf<OrderPaymentLiveness.Attempting>()

            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(102L to liveness.of(102L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("오래된 READY(readyTtl 초과)에 가려지지 않고 재결제 시도 시각 기준으로 만료되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("예약 생성과 함께 만들어진 원 PENDING payment가 여전히 PENDING일 때 (2차 무력화 재발 방지 회귀)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        // 원 payment의 createdAt은 booking.createdAt과 거의 동일하다
        // (CreateBookingUseCase.persistBookingAndPendingPayment — 같은 트랜잭션에서 생성).
        val bookingCreatedAt = ZonedDateTime.now().minusMinutes(70)
        val candidates = listOf(
            BookingExpiryCandidate(bookingId = 101L, createdAt = bookingCreatedAt),
        )

        When("filterExpirable을 호출하면 (liveness=Attempting, since=예약 생성 시각과 거의 동일 — 70분 전)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(101L to OrderPaymentLiveness.Attempting(bookingCreatedAt.plusSeconds(1))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("Attempting에 면제를 주는 게 아니므로 빠른 TTL(15분)에 그대로 걸려 만료된다") {
                result.expirableIds shouldBe listOf(101L)
            }
        }
    }

    Given("만료 후보가 비어있을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = emptyList(),
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("빈 목록을 반환한다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("TTL이 지난 PENDING 예약이 있을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        every { bookingRepository.tryExpire(1L) } returns true

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(1L))

            Then("CAS 조건부 UPDATE로 전이되어 슬롯 점유(활성 카운트)에서 제외된다") {
                expiredCount shouldBe 1
                verify(exactly = 1) { bookingRepository.tryExpire(1L) }
            }
        }
    }

    Given("이미 PENDING이 아닌(CONFIRMED 등) 예약에") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        every { bookingRepository.tryExpire(2L) } returns false

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(2L))

            Then("CAS 조건(WHERE status=PENDING)에 걸리지 않아 영향 행 0건으로 멱등하게 처리된다") {
                expiredCount shouldBe 0
            }
        }
    }

    Given("만료 대상 id 목록이 비어있을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(emptyList())

            Then("조회·CAS 쓰기 없이 0을 반환한다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { bookingRepository.tryExpire(any()) }
            }
        }
    }

    Given("여러 건 중 일부만 PENDING 그대로 남아있을 때(청크 커밋 중 CONFIRMED로 경합)") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        every { bookingRepository.tryExpire(3L) } returns true
        every { bookingRepository.tryExpire(4L) } returns false

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(3L, 4L))

            Then("PENDING인 건만 카운트되고 경합에서 진 건은 오만료되지 않는다") {
                expiredCount shouldBe 1
            }
        }
    }

    Given("booking.expiry.enabled 플래그가 true일 때") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(mockk(), featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("booking.expiry.enabled", FeatureContext.anonymous(), true)
        } returns true

        When("isExpiryEnabled를 호출하면") {
            val enabled = service.isExpiryEnabled()

            Then("true를 반환한다") {
                enabled shouldBe true
            }
        }
    }

    Given("booking.expiry.enabled 플래그가 false일 때(운영 킬 스위치)") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(mockk(), featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("booking.expiry.enabled", FeatureContext.anonymous(), true)
        } returns false

        When("isExpiryEnabled를 호출하면") {
            val enabled = service.isExpiryEnabled()

            Then("false를 반환한다 (재기동 없이 즉시 반영)") {
                enabled shouldBe false
            }
        }
    }
})
