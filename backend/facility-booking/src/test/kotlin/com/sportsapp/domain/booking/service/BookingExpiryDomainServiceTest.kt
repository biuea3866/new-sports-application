package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import java.time.ZonedDateTime

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼가 사용하는 [BookingDomainService]의
 * 후보 조회(findExpirableBookingIds)·만료 전이(expireBookings)·킬 스위치(isExpiryEnabled)를
 * 검증한다.
 *
 * 슬롯 점유 해제는 별도 보상 로직이 아니라, PENDING → EXPIRED 전이 자체로 완료된다 —
 * 슬롯 점유는 countBySlotIdAndStatusIn(PENDING, CONFIRMED)로 파생되므로 EXPIRED로 전이되면
 * 그 즉시 활성 카운트에서 제외된다.
 *
 * 만료 전이는 [BookingRepository.tryExpire] CAS(조건부 UPDATE, WHERE status='PENDING')로
 * 수행한다 — 청크 트랜잭션이 REPEATABLE READ 스냅샷을 뜬 이후 다른 트랜잭션이 커밋한 CONFIRMED를
 * EXPIRED로 덮어쓰는 lost update를 막는다(리뷰 ②). find→mutate→save 경로를 쓰지 않으므로
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

    Given("TTL 분·커서·조회 상한이 주어졌을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val thresholdSlot = slot<ZonedDateTime>()
        every { bookingRepository.findPendingCreatedBefore(capture(thresholdSlot), 5L, 100) } returns listOf(10L, 11L)

        When("findExpirableBookingIds를 호출하면") {
            val result = service.findExpirableBookingIds(ttlMinutes = 15, afterId = 5L, limit = 100)

            Then("BookingRepository 조회 결과를 그대로 반환한다") {
                result shouldBe listOf(10L, 11L)
            }

            Then("TTL 임계값이 now - 15분 근방으로 이 메서드 내부에서 계산된다 (no-time-parameter)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(15)).abs().seconds
                (diff < 5) shouldBe true
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
