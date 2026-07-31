package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.common.payment.OrderPaymentLiveness
import com.sportsapp.domain.common.security.AuthChannelResolver
import com.sportsapp.domain.goods.GoodsFeatureFlagKeys
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryCandidate
import com.sportsapp.domain.goods.dto.GoodsOrderExpiryTtlPolicy
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.repository.PopularProductsCache
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import java.time.Duration
import java.time.ZonedDateTime

/** 순수 단위 테스트에서 JPA 생성 전략으로 채워질 id를 강제 주입한다. */
private fun <T : Any> forceId(entity: T, id: Long): T {
    val idField = entity.javaClass.getDeclaredField("id")
    idField.isAccessible = true
    idField.set(entity, id)
    return entity
}

/**
 * W1-11a — goods PENDING 주문 만료 스위퍼가 사용하는 [GoodsDomainService]의 후보 조회
 * (findExpirableGoodsOrderCandidates)·최종 판정(filterExpirable)·만료 전이(expireOrders)·
 * 킬 스위치(isExpiryEnabled)를 검증한다.
 *
 * `facility-booking`(W1-11c) `BookingExpiryDomainServiceTest`가 정본이다 — 판정 로직
 * (settled 우선·Live의 두 창 AND 결합·단조성)은 [OrderPaymentLiveness.allowsExpiry]에
 * 캡슐화되어 있으므로 이 테스트는 그 위임이 실제로 이뤄지는지와 goods 고유 책임(TTL 임계값
 * 계산·재고 복원 CAS 연동)만 검증한다.
 *
 * booking과 달리 goods는 만료 시 **재고 복원**이 필요하다(슬롯 점유처럼 상태만으로 파생되지
 * 않는다) — [expireOrders]가 [GoodsOrderCustomRepository.tryExpire] CAS 성공 시에만 재고를
 * 복원해, CAS 경합에서 진 건(이미 다른 트랜잭션이 CONFIRMED로 전이시킨 건)은 재고를
 * 건드리지 않는다(재고 이중 복원 방지 — 이 티켓의 CAS 요구사항 핵심).
 */
class GoodsOrderExpiryDomainServiceTest : BehaviorSpec({

    fun buildService(
        goodsOrderRepository: GoodsOrderRepository = mockk(),
        goodsOrderCustomRepository: GoodsOrderCustomRepository = mockk(),
        goodsOrderItemRepository: GoodsOrderItemRepository = mockk(),
        stockRepository: StockRepository = mockk(),
        featureFlagEvaluator: FeatureFlagEvaluator = mockk(),
    ): GoodsDomainService = GoodsDomainService(
        productRepository = mockk<ProductRepository>(),
        stockRepository = stockRepository,
        productCustomRepository = mockk<ProductCustomRepository>(),
        popularProductsCache = mockk<PopularProductsCache>(),
        goodsOrderRepository = goodsOrderRepository,
        goodsOrderItemRepository = goodsOrderItemRepository,
        goodsOrderCustomRepository = goodsOrderCustomRepository,
        limitedDropRepository = mockk<LimitedDropRepository>(),
        authChannelResolver = mockk<AuthChannelResolver>(),
        dropReservationStore = mockk<DropReservationStore>(),
        featureFlagEvaluator = featureFlagEvaluator,
    )

    val defaultTtlPolicy = GoodsOrderExpiryTtlPolicy(ttlMinutes = 30, readyTtlMinutes = 90)

    Given("TTL 분·커서·조회 상한이 주어졌을 때") {
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val service = buildService(goodsOrderCustomRepository = goodsOrderCustomRepository)
        val thresholdSlot = slot<ZonedDateTime>()
        val candidates = listOf(
            GoodsOrderExpiryCandidate(orderId = 10L, createdAt = ZonedDateTime.now().minusMinutes(40)),
            GoodsOrderExpiryCandidate(orderId = 11L, createdAt = ZonedDateTime.now().minusMinutes(31)),
        )
        every { goodsOrderCustomRepository.findPendingCreatedBefore(capture(thresholdSlot), 5L, 100) } returns candidates

        When("findExpirableGoodsOrderCandidates를 호출하면") {
            val result = service.findExpirableGoodsOrderCandidates(ttlMinutes = 30, afterId = 5L, limit = 100)

            Then("GoodsOrderCustomRepository 조회 결과를 그대로 반환한다") {
                result shouldBe candidates
            }

            Then("TTL 임계값이 now - 30분 근방으로 이 메서드 내부에서 계산된다 (no-time-parameter)") {
                val diff = Duration.between(thresholdSlot.captured, ZonedDateTime.now().minusMinutes(30)).abs().seconds
                (diff < 5) shouldBe true
            }
        }
    }

    Given("결제가 settled(완료)인 후보가 있을 때 (절대 만료 금지)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 1L, createdAt = ZonedDateTime.now().minusMinutes(200)))

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

    Given("결제가 live(READY)이고 발급 시각이 readyTtl을 아직 지나지 않았을 때 (오만료 방지)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 2L, createdAt = ZonedDateTime.now().minusMinutes(40)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(10)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(2L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에서 제외된다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 live(READY)이고 발급 시각이 readyTtl(90분)을 지났고 재결제 시도(attemptSince)가 없을 때") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 3L, createdAt = ZonedDateTime.now().minusMinutes(200)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(100)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(3L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(3L)
            }
        }
    }

    Given("결제가 live인 readyTtl을 지났지만 방금 재결제 시도(attemptSince)가 빠른 TTL 이내일 때 (단조성 — 세 번째 가림 방지)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 4L, createdAt = ZonedDateTime.now().minusMinutes(200)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(100)
        val attemptAnchor = ZonedDateTime.now().minusMinutes(5)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(4L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = attemptAnchor)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에서 제외된다 (두 창 모두 검사 — Live의 attemptSince 항 누락 재발 방지)") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 live인 readyTtl을 지났고 재결제 시도(attemptSince)도 빠른 TTL을 지났을 때") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 5L, createdAt = ZonedDateTime.now().minusMinutes(200)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(100)
        val attemptAnchor = ZonedDateTime.now().minusMinutes(40)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(5L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = attemptAnchor)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("두 창이 모두 지나 만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(5L)
            }
        }
    }

    Given("결제가 attempting(PENDING 재결제 시도)이고 빠른 TTL 이내일 때") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 6L, createdAt = ZonedDateTime.now().minusMinutes(60)))
        val attemptAnchor = ZonedDateTime.now().minusMinutes(10)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(6L to OrderPaymentLiveness.Attempting(since = attemptAnchor)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에서 제외된다 (재결제 시도 중 오만료 방지)") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("결제가 attempting이고 빠른 TTL을 지났을 때") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 7L, createdAt = ZonedDateTime.now().minusMinutes(60)))
        val attemptAnchor = ZonedDateTime.now().minusMinutes(40)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(7L to OrderPaymentLiveness.Attempting(since = attemptAnchor)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(7L)
            }
        }
    }

    Given("결제가 FAILED인 30분 경과 주문일 때 (핵심 회귀 — F-A 타격, liveness 맵에 없음)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 8L, createdAt = ZonedDateTime.now().minusMinutes(31)))

        When("filterExpirable을 호출하면 (liveness 맵에 없는 후보는 None으로 처리)") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("고립 주문(F-A)이 정확히 만료 대상에 포함된다") {
                result.expirableIds shouldBe listOf(8L)
            }
        }
    }

    Given("결제 이력이 없는 주문이 빠른 TTL 이내일 때 (None, 경계값)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 9L, createdAt = ZonedDateTime.now().minusMinutes(10)))

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = emptyMap(),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("만료 대상에서 제외된다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("payment가 FAILED인 30분 경과 주문일 때 (티켓 필수 회귀 — F-A 타격, 취소 후 재고 복원까지 종단 검증)") {
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = buildService(
            goodsOrderCustomRepository = goodsOrderCustomRepository,
            goodsOrderItemRepository = goodsOrderItemRepository,
            stockRepository = stockRepository,
        )
        // FAILED payment는 liveness 맵에 없는 None으로 취급된다(F-A 정확 타격).
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 50L, createdAt = ZonedDateTime.now().minusMinutes(31)))
        val item = forceId(GoodsOrderItem(orderId = 50L, productId = 500L, quantity = 3, unitPrice = BigDecimal("2000")), 50L)
        val stock = mockk<com.sportsapp.domain.goods.entity.Stock>(relaxed = true)
        every { goodsOrderCustomRepository.tryExpire(50L) } returns true
        every { goodsOrderItemRepository.findByOrderId(50L) } returns listOf(item)
        every { stockRepository.findByProductId(500L) } returns stock
        every { stockRepository.save(any()) } returns stock
        every { goodsOrderItemRepository.saveAll(any()) } returns emptyList()

        When("filterExpirable 후 expireOrders를 호출하면") {
            val filterResult = service.filterExpirable(candidates = candidates, liveness = emptyMap(), ttlPolicy = defaultTtlPolicy)
            val expiredCount = service.expireOrders(filterResult.expirableIds)

            Then("고립 주문이 취소되고 재고가 복원된다") {
                filterResult.expirableIds shouldBe listOf(50L)
                expiredCount shouldBe 1
                verify(exactly = 1) { stock.restore(3) }
            }
        }
    }

    Given("live payment 발급 40분 경과 주문일 때 (티켓 필수 회귀 — 오만료 방지)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 51L, createdAt = ZonedDateTime.now().minusMinutes(200)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(40)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(51L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("readyTtl(90분)을 아직 지나지 않아 취소되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("live payment 발급 100분 경과 주문일 때 (티켓 필수 회귀 — 무한 점유 방지)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 52L, createdAt = ZonedDateTime.now().minusMinutes(200)))
        val liveAnchor = ZonedDateTime.now().minusMinutes(100)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(52L to OrderPaymentLiveness.Live(since = liveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("readyTtl(90분)을 지나 취소된다") {
                result.expirableIds shouldBe listOf(52L)
            }
        }
    }

    Given("100분 전 생성된 주문에 방금 새 READY payment가 생겼을 때 (티켓 필수 회귀 — 재결제 오취소 방지, K1/K2)") {
        val service = buildService()
        val candidates = listOf(GoodsOrderExpiryCandidate(orderId = 53L, createdAt = ZonedDateTime.now().minusMinutes(100)))
        val freshLiveAnchor = ZonedDateTime.now().minusMinutes(2)

        When("filterExpirable을 호출하면") {
            val result = service.filterExpirable(
                candidates = candidates,
                liveness = mapOf(53L to OrderPaymentLiveness.Live(since = freshLiveAnchor, attemptSince = null)),
                ttlPolicy = defaultTtlPolicy,
            )

            Then("주문 생성 시각이 아니라 payment 발급 시각(방금)이 앵커가 되어 취소되지 않는다") {
                result.expirableIds shouldBe emptyList()
            }
        }
    }

    Given("만료 판정된 주문 id 목록이 주어지고 CAS 전이가 모두 성공할 때") {
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = buildService(
            goodsOrderCustomRepository = goodsOrderCustomRepository,
            goodsOrderItemRepository = goodsOrderItemRepository,
            stockRepository = stockRepository,
        )

        val item1 = forceId(GoodsOrderItem(orderId = 1L, productId = 100L, quantity = 2, unitPrice = BigDecimal("5000")), 1L)
        val stock1 = mockk<com.sportsapp.domain.goods.entity.Stock>(relaxed = true)
        every { goodsOrderCustomRepository.tryExpire(1L) } returns true
        every { goodsOrderItemRepository.findByOrderId(1L) } returns listOf(item1)
        every { stockRepository.findByProductId(100L) } returns stock1
        every { stockRepository.save(any()) } returns stock1
        every { goodsOrderItemRepository.saveAll(any()) } returns emptyList()

        When("expireOrders를 호출하면") {
            val expiredCount = service.expireOrders(listOf(1L))

            Then("CAS 성공 건수를 반환하고 재고가 복원된다") {
                expiredCount shouldBe 1
                verify(exactly = 1) { stock1.restore(2) }
                verify(exactly = 1) { stockRepository.save(stock1) }
            }

            Then("아이템이 soft-delete된 채로 saveAll이 호출된다") {
                item1.isDeleted shouldBe true
                verify(exactly = 1) { goodsOrderItemRepository.saveAll(any()) }
            }
        }
    }

    Given("CAS 경합에서 진 주문 id가 섞여 있을 때 (재고 이중 복원 방지 — 핵심 회귀)") {
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = buildService(
            goodsOrderCustomRepository = goodsOrderCustomRepository,
            goodsOrderItemRepository = goodsOrderItemRepository,
            stockRepository = stockRepository,
        )

        // 12L은 CAS 성공, 13L은 webhook 확정이 먼저 CONFIRMED로 전이시켜 CAS(WHERE status=PENDING)에 진다.
        val item12 = forceId(GoodsOrderItem(orderId = 12L, productId = 100L, quantity = 1, unitPrice = BigDecimal("1000")), 12L)
        val stock12 = mockk<com.sportsapp.domain.goods.entity.Stock>(relaxed = true)
        every { goodsOrderCustomRepository.tryExpire(12L) } returns true
        every { goodsOrderCustomRepository.tryExpire(13L) } returns false
        every { goodsOrderItemRepository.findByOrderId(12L) } returns listOf(item12)
        every { stockRepository.findByProductId(100L) } returns stock12
        every { stockRepository.save(any()) } returns stock12
        every { goodsOrderItemRepository.saveAll(any()) } returns emptyList()

        When("expireOrders를 호출하면") {
            val expiredCount = service.expireOrders(listOf(12L, 13L))

            Then("CAS 성공 건(12L)만 만료·재고 복원되고 경합에 진 건(13L)은 건드리지 않는다") {
                expiredCount shouldBe 1
                verify(exactly = 1) { stockRepository.findByProductId(100L) }
                verify(exactly = 0) { goodsOrderItemRepository.findByOrderId(13L) }
            }
        }
    }

    Given("만료 대상 id 목록이 빈 목록일 때 (엣지)") {
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val service = buildService(goodsOrderCustomRepository = goodsOrderCustomRepository)

        When("expireOrders를 호출하면") {
            val expiredCount = service.expireOrders(emptyList())

            Then("CAS 호출 없이 0을 반환한다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { goodsOrderCustomRepository.tryExpire(any()) }
            }
        }
    }

    Given("goods.expiry.enabled 플래그가 활성화되어 있을 때") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled(GoodsFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)
        } returns true

        When("isExpiryEnabled를 호출하면") {
            Then("true를 반환한다") {
                service.isExpiryEnabled() shouldBe true
            }
        }
    }

    Given("goods.expiry.enabled 플래그가 비활성화(운영 킬 스위치)되어 있을 때") {
        val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()
        val service = buildService(featureFlagEvaluator = featureFlagEvaluator)
        every {
            featureFlagEvaluator.isEnabled(GoodsFeatureFlagKeys.EXPIRY_ENABLED, FeatureContext.anonymous(), true)
        } returns false

        When("isExpiryEnabled를 호출하면") {
            Then("false를 반환한다") {
                service.isExpiryEnabled() shouldBe false
            }
        }
    }
})
