package com.sportsapp.domain.goods.service

import com.sportsapp.domain.common.security.AuthChannelResolver
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.goods.gateway.DropReservationStore
import com.sportsapp.domain.goods.repository.GoodsOrderCustomRepository
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.LimitedDropRepository
import com.sportsapp.domain.goods.repository.PopularProductsCache
import com.sportsapp.domain.goods.repository.ProductCustomRepository
import com.sportsapp.domain.goods.repository.ProductRepository
import com.sportsapp.domain.goods.repository.StockRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal

/**
 * W1-11a — markPaid는 [GoodsOrderCustomRepository.tryConfirm] CAS(조건부 UPDATE, WHERE
 * status='PENDING')로 전이한다. `facility-booking`(W1-11c)의 `confirmBooking`과 동일한
 * 이유 — 비잠금 findById → markPaid() → save() 경로는 만료 스위퍼가 먼저 커밋한
 * CANCELLED(+재고 복원)를 조건 없는 dirty-checking UPDATE로 덮어써, 이미 다른 곳에
 * 풀린 재고를 CONFIRMED 주문이 차지한 것처럼 보이게 하는 반대 방향 lost update(재고
 * 이중 차감)를 만들 수 있어 tryExpire와 대칭으로 CAS로 닫는다.
 */
class GoodsOrderConfirmDomainServiceTest : BehaviorSpec({

    fun buildService(
        goodsOrderRepository: GoodsOrderRepository,
        goodsOrderCustomRepository: GoodsOrderCustomRepository,
    ) = GoodsDomainService(
        productRepository = mockk<ProductRepository>(),
        stockRepository = mockk<StockRepository>(),
        productCustomRepository = mockk<ProductCustomRepository>(),
        popularProductsCache = mockk<PopularProductsCache>(),
        goodsOrderRepository = goodsOrderRepository,
        goodsOrderItemRepository = mockk<GoodsOrderItemRepository>(),
        goodsOrderCustomRepository = goodsOrderCustomRepository,
        limitedDropRepository = mockk<LimitedDropRepository>(),
        authChannelResolver = mockk<AuthChannelResolver>(),
        dropReservationStore = mockk<DropReservationStore>(),
        featureFlagEvaluator = mockk(),
    )

    Given("PENDING 상태의 GoodsOrder를 markPaid로 확정할 때") {
        val goodsOrderRepository = mockk<GoodsOrderRepository>()
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val service = buildService(goodsOrderRepository, goodsOrderCustomRepository)

        val confirmedOrder = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000"))
        confirmedOrder.markPaid(999L)
        every { goodsOrderCustomRepository.tryConfirm(orderId = 1L, paymentId = 999L) } returns true
        every { goodsOrderRepository.findById(1L) } returns confirmedOrder

        When("markPaid를 호출하면") {
            val result = service.markPaid(orderId = 1L, paymentId = 999L)

            Then("CAS 전이가 성공해 CONFIRMED로 반환된다") {
                result.status shouldBe GoodsOrderStatus.CONFIRMED
                result.paymentId shouldBe 999L
            }
        }
    }

    Given("이미 같은 paymentId로 CONFIRMED된 GoodsOrder에 markPaid를 재호출할 때 (webhook 중복 — 멱등)") {
        val goodsOrderRepository = mockk<GoodsOrderRepository>()
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val service = buildService(goodsOrderRepository, goodsOrderCustomRepository)

        val order = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000"))
        order.markPaid(200L)
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 CONFIRMED).
        every { goodsOrderCustomRepository.tryConfirm(orderId = 2L, paymentId = 200L) } returns false
        every { goodsOrderRepository.findById(2L) } returns order

        When("markPaid를 재호출하면") {
            val result = service.markPaid(orderId = 2L, paymentId = 200L)

            Then("멱등하게 처리되어 기존 paymentId가 유지된 CONFIRMED가 반환된다") {
                result.status shouldBe GoodsOrderStatus.CONFIRMED
                result.paymentId shouldBe 200L
            }
        }
    }

    Given("만료 스위퍼가 먼저 CANCELLED로 전이시킨 뒤 markPaid가 뒤늦게 도착할 때 (핵심 회귀 — 반대 방향 lost update 방지)") {
        val goodsOrderRepository = mockk<GoodsOrderRepository>()
        val goodsOrderCustomRepository = mockk<GoodsOrderCustomRepository>()
        val service = buildService(goodsOrderRepository, goodsOrderCustomRepository)

        val cancelledOrder = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000"))
        cancelledOrder.cancel()
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 CANCELLED로 전이됨).
        every { goodsOrderCustomRepository.tryConfirm(orderId = 4L, paymentId = 300L) } returns false
        every { goodsOrderRepository.findById(4L) } returns cancelledOrder

        When("markPaid를 호출하면") {
            Then("InvalidGoodsOrderStateException을 던져 CONFIRMED로 덮어쓰지 않는다 (재고 이중 차감 방지)") {
                shouldThrow<InvalidGoodsOrderStateException> {
                    service.markPaid(orderId = 4L, paymentId = 300L)
                }
            }
        }
    }
})
