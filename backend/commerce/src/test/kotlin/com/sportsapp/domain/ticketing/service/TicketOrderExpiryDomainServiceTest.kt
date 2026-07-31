package com.sportsapp.domain.ticketing.service

import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.payment.dto.PaymentLivenessRow
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentLivenessClassifier
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryCandidate
import com.sportsapp.domain.ticketing.dto.TicketOrderExpiryTtlPolicy
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.gateway.SeatLockStore
import com.sportsapp.domain.ticketing.repository.EventCustomRepository
import com.sportsapp.domain.ticketing.repository.EventRepository
import com.sportsapp.domain.ticketing.repository.SeatCustomRepository
import com.sportsapp.domain.ticketing.repository.SeatRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderCustomRepository
import com.sportsapp.domain.ticketing.repository.TicketOrderRepository
import com.sportsapp.domain.ticketing.repository.TicketRepository
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
 * W1-11b — ticketing PENDING 주문 만료 스위퍼가 사용하는 [TicketingDomainService]의
 * 후보 조회(findExpirableTicketOrderCandidates)·최종 판정(filterExpirableTicketOrders)·
 * 만료 전이(expireTicketOrders)·킬 스위치(isExpiryEnabled)를 검증한다. booking(W1-11c)의
 * `BookingExpiryDomainServiceTest`와 동일한 구조 — 판정 로직은 재구현하지 않고
 * [OrderPaymentLiveness.allowsExpiry]에 위임하므로, 이 테스트는 위임이 올바른 인자로
 * 이뤄지는지와 ticketing 고유의 좌석 락 해제(Redis)를 검증한다.
 *
 * 만료 전이는 [TicketOrderRepository.tryExpire] CAS(조건부 UPDATE, WHERE status='PENDING')로
 * 수행한다. find→mutate→save 경로를 쓰지 않으므로 tryExpire 반환값(affected rows > 0)만으로
 * 전이 성공 여부를 검증한다.
 */
class TicketOrderExpiryDomainServiceTest : BehaviorSpec({

    fun buildService(
        ticketOrderRepository: TicketOrderRepository = mockk(relaxed = true),
        seatLockStore: SeatLockStore = mockk(relaxed = true),
        featureFlagEvaluator: FeatureFlagEvaluator = mockk(),
    ): TicketingDomainService = TicketingDomainService(
        eventRepository = mockk<EventRepository>(relaxed = true),
        seatRepository = mockk<SeatRepository>(relaxed = true),
        eventCustomRepository = mockk<EventCustomRepository>(relaxed = true),
        seatCustomRepository = mockk<SeatCustomRepository>(relaxed = true),
        ticketOrderCustomRepository = mockk<TicketOrderCustomRepository>(relaxed = true),
        seatLockStore = seatLockStore,
        ticketOrderRepository = ticketOrderRepository,
        ticketRepository = mockk<TicketRepository>(relaxed = true),
        domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true),
        featureFlagEvaluator = featureFlagEvaluator,
    )

    val defaultTtlPolicy = TicketOrderExpiryTtlPolicy(ttlMinutes = 15, readyTtlMinutes = 60)

    Given("TTL 분·커서·조회 상한이 주어졌을 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)
        val thresholdSlot = slot<ZonedDateTime>()
        val candidates = listOf(
            TicketOrderExpiryCandidate(orderId = 10L, createdAt = ZonedDateTime.now().minusMinutes(20)),
            TicketOrderExpiryCandidate(orderId = 11L, createdAt = ZonedDateTime.now().minusMinutes(16)),
        )
        every { ticketOrderRepository.findPendingCreatedBefore(capture(thresholdSlot), 5L, 100) } returns candidates

        When("findExpirableTicketOrderCandidates를 호출하면") {
            val result = service.findExpirableTicketOrderCandidates(ttlMinutes = 15, afterId = 5L, limit = 100)

            Then("TicketOrderRepository 조회 결과를 그대로 반환한다") {
                result shouldBe candidates
            }

            Then("TTL 임계값이 now - 15분 근방으로 이 메서드 내부에서 계산된다 (no-time-parameter)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(15)).abs().seconds
                (diff < 5) shouldBe true
            }
        }
    }

    Given("결제가 settled(완료)인 후보가 있을 때 (절대 만료 금지)") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 1L, createdAt = ZonedDateTime.now().minusMinutes(100)))

        When("filterExpirableTicketOrders를 호출하면 (Settled에 포함, 앵커가 아무리 오래돼도)") {
            val result = service.filterExpirableTicketOrders(
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
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 2L, createdAt = ZonedDateTime.now().minusMinutes(20)))

        When("filterExpirableTicketOrders를 호출하면 (readyTtlMinutes=60, payment 발급 시각은 20분 전)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(since = ZonedDateTime.now().minusMinutes(20), attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("결제창에 머무는 사용자로 보아 만료 대상에서 제외된다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 live(READY)이지만 발급 시각이 readyTtl을 지났을 때 (무한 점유 방지 — 핵심 회귀)") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 3L, createdAt = ZonedDateTime.now().minusMinutes(70)))

        When("filterExpirableTicketOrders를 호출하면 (readyTtlMinutes=60, payment 발급 시각은 70분 전)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(3L to OrderPaymentLiveness.Live(since = ZonedDateTime.now().minusMinutes(70), attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("느린 TTL도 지나 만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(3L)
            }
        }
    }

    Given("결제가 FAILED인 15분 경과 주문이 있을 때 (F-A 타격 — 핵심 회귀)") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 5L, createdAt = ZonedDateTime.now().minusMinutes(16)))
        // PG prepare 실패로 즉시 FAILED — classify는 종결 상태를 None으로 분류한다.
        val liveness = PaymentLivenessClassifier.classify(
            listOf(PaymentLivenessRow(orderId = 5L, status = PaymentStatus.FAILED, createdAt = ZonedDateTime.now().minusMinutes(16))),
        )

        When("filterExpirableTicketOrders를 호출하면") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(5L to liveness.of(5L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("고립 주문(F-A)이 빠른 TTL로 정확히 만료 대상이 된다") {
                liveness.of(5L).shouldBeInstanceOf<OrderPaymentLiveness.None>()
                result.expirableIds shouldBe listOf(5L)
            }
        }
    }

    Given("결제 시도 이력이 없거나 전부 종결(None)일 때") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 4L, createdAt = ZonedDateTime.now().minusMinutes(16)))

        When("filterExpirableTicketOrders를 호출하면 (liveness가 None, readyTtlMinutes=60 미도달이어도)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(4L to OrderPaymentLiveness.None),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("빠른 TTL(주문 생성 시각 기준)로 이미 만료 대상이다") {
                result.expirableIds shouldBe listOf(4L)
            }
        }
    }

    Given("liveness 맵에 아예 없는(=payment 행 없음, None과 동치) 후보일 때") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 40L, createdAt = ZonedDateTime.now().minusMinutes(16)))

        When("filterExpirableTicketOrders를 호출하면 (liveness 맵에 해당 orderId 자체가 없음)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("None과 동일하게 빠른 TTL로 만료 대상이다") {
                result.expirableIds shouldBe listOf(40L)
            }
        }
    }

    Given("70분 전 생성된 주문에 방금 새 READY payment가 생겼을 때 (재결제 오만료 방지 — 핵심 회귀)") {
        val service = buildService()
        // 주문(order.createdAt)은 70분 전에 생성됐지만, 방금(1분 전) 새 payment가 READY로
        // 발급됐다 — POST /payments/prepare가 기존 주문에 새 payment 행을 만드는 가동 중
        // 경로다(mobile/app/payment/new.tsx).
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 6L, createdAt = ZonedDateTime.now().minusMinutes(70)))

        When("filterExpirableTicketOrders를 호출하면 (readyTtlMinutes=60, payment 발급 시각은 1분 전)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(6L to OrderPaymentLiveness.Live(since = ZonedDateTime.now().minusMinutes(1), attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("주문 생성 시각과 무관하게 payment 발급 시각 기준으로 만료되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("70분 전 생성된 주문에 방금(재결제 시도 중) 새 PENDING payment가 삽입됐을 때 (재결제 시도 시각을 빠른 TTL 앵커로 삼아 오만료를 방지한다)") {
        val service = buildService()
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 100L, createdAt = ZonedDateTime.now().minusMinutes(70)))

        When("filterExpirableTicketOrders를 호출하면 (liveness=Attempting, since=5초 전)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(100L to OrderPaymentLiveness.Attempting(ZonedDateTime.now().minusSeconds(5))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("주문 생성 시각과 무관하게 재결제 시도 시각 기준으로 만료되지 않는다 — max(createdAt, attemptSince) 앵커") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("주문 생성과 함께 만들어진 원 PENDING payment가 여전히 PENDING일 때 (attempting에 면제를 주지 않고 빠른 TTL로 정상 만료된다)") {
        val service = buildService()
        // 원 payment의 createdAt은 order.createdAt과 거의 동일하다
        // (PurchaseTicketsUseCase — 같은 요청에서 생성).
        val orderCreatedAt = ZonedDateTime.now().minusMinutes(70)
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 101L, createdAt = orderCreatedAt))

        When("filterExpirableTicketOrders를 호출하면 (liveness=Attempting, since=주문 생성 시각과 거의 동일 — 70분 전)") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(101L to OrderPaymentLiveness.Attempting(orderCreatedAt.plusSeconds(1))),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("Attempting에 면제를 주는 게 아니므로 빠른 TTL(15분)에 그대로 걸려 만료된다") {
                result.expirableIds shouldBe listOf(101L)
            }
        }
    }

    Given("60분 지난 READY 주문에 방금 재결제 시도가 삽입됐을 때 (승자를 고르지 않고 live·attempting 양쪽 앵커를 모두 검사한다)") {
        val service = buildService()
        val staleReadyAt = ZonedDateTime.now().minusMinutes(65)
        val retryAttemptAt = ZonedDateTime.now().minusSeconds(5)
        val liveness = PaymentLivenessClassifier.classify(
            listOf(
                PaymentLivenessRow(orderId = 102L, status = PaymentStatus.READY, createdAt = staleReadyAt),
                PaymentLivenessRow(orderId = 102L, status = PaymentStatus.PENDING, createdAt = retryAttemptAt),
            ),
        )
        val candidates = listOf(TicketOrderExpiryCandidate(orderId = 102L, createdAt = staleReadyAt))

        When("classify 결과를 filterExpirableTicketOrders에 그대로 넘기면") {
            val result = service.filterExpirableTicketOrders(
                candidates = candidates,
                liveness = mapOf(102L to liveness.of(102L)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("classify는 승자를 고르지 않고 Live(since=staleReadyAt, attemptSince=retryAttemptAt)를 반환한다") {
                val classified = liveness.of(102L).shouldBeInstanceOf<OrderPaymentLiveness.Live>()
                classified.since shouldBe staleReadyAt
                classified.attemptSince shouldBe retryAttemptAt
            }

            Then("빠른 TTL(재결제 시도 시각 기준)까지는 아직 지나지 않아 만료되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("만료 후보가 비어있을 때") {
        val service = buildService()

        When("filterExpirableTicketOrders를 호출하면") {
            val result = service.filterExpirableTicketOrders(candidates = emptyList(), liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)

            Then("빈 목록을 반환한다") {
                result.expirableIds shouldBe emptyList()
                result.skippedSettledCount shouldBe 0
            }
        }
    }

    Given("TTL이 지난 PENDING 주문이 있을 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val seatLockStore = mockk<SeatLockStore>(relaxed = true)
        val service = buildService(ticketOrderRepository = ticketOrderRepository, seatLockStore = seatLockStore)
        val order = TicketOrder(
            userId = 7L,
            status = OrderStatus.CANCELLED,
            paymentId = null,
            lockedEventId = 1L,
            lockedSeatIds = listOf(101L, 102L),
        )
        every { ticketOrderRepository.tryExpire(1L) } returns true
        every { ticketOrderRepository.findById(1L) } returns order

        When("expireTicketOrders를 호출하면") {
            val expiredCount = service.expireTicketOrders(listOf(1L))

            Then("CAS 조건부 UPDATE로 전이된다") {
                expiredCount shouldBe 1
                verify(exactly = 1) { ticketOrderRepository.tryExpire(1L) }
            }

            Then("남아있을 수 있는 Redis 좌석 락이 방어적으로 해제된다 (기존 취소 경로 재사용)") {
                verify(exactly = 1) { seatLockStore.unlock(1L, 101L, 7L) }
                verify(exactly = 1) { seatLockStore.unlock(1L, 102L, 7L) }
            }
        }
    }

    Given("이미 PENDING이 아닌(CONFIRMED 등) 주문에") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)
        every { ticketOrderRepository.tryExpire(2L) } returns false

        When("expireTicketOrders를 호출하면") {
            val expiredCount = service.expireTicketOrders(listOf(2L))

            Then("CAS 조건(WHERE status=PENDING)에 걸리지 않아 영향 행 0건으로 멱등하게 처리된다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { ticketOrderRepository.findById(any()) }
            }
        }
    }

    Given("만료 대상 id 목록이 비어있을 때") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)

        When("expireTicketOrders를 호출하면") {
            val expiredCount = service.expireTicketOrders(emptyList())

            Then("조회·CAS 쓰기 없이 0을 반환한다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { ticketOrderRepository.tryExpire(any()) }
            }
        }
    }

    Given("여러 건 중 일부만 PENDING 그대로 남아있을 때(청크 커밋 중 CONFIRMED로 경합)") {
        val ticketOrderRepository = mockk<TicketOrderRepository>()
        val service = buildService(ticketOrderRepository = ticketOrderRepository)
        every { ticketOrderRepository.tryExpire(3L) } returns true
        every { ticketOrderRepository.findById(3L) } returns TicketOrder(
            userId = 1L, status = OrderStatus.CANCELLED, paymentId = null, lockedEventId = 1L, lockedSeatIds = listOf(1L),
        )
        every { ticketOrderRepository.tryExpire(4L) } returns false

        When("expireTicketOrders를 호출하면") {
            val expiredCount = service.expireTicketOrders(listOf(3L, 4L))

            Then("PENDING인 건만 카운트되고 경합에서 진 건은 오만료되지 않는다") {
                expiredCount shouldBe 1
            }
        }
    }

    Given("ticketing.expiry.enabled 플래그가 true일 때") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("ticketing.expiry.enabled", FeatureContext.anonymous(), true)
        } returns true

        When("isExpiryEnabled를 호출하면") {
            val enabled = service.isExpiryEnabled()

            Then("true를 반환한다") {
                enabled shouldBe true
            }
        }
    }

    Given("ticketing.expiry.enabled 플래그가 false일 때(운영 킬 스위치)") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled("ticketing.expiry.enabled", FeatureContext.anonymous(), true)
        } returns false

        When("isExpiryEnabled를 호출하면") {
            val enabled = service.isExpiryEnabled()

            Then("false를 반환한다 (재기동 없이 즉시 반영)") {
                enabled shouldBe false
            }
        }
    }
})
