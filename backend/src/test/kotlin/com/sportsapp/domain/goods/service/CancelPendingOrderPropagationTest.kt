package com.sportsapp.domain.goods.service

import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.exception.InvalidGoodsOrderStateException
import com.sportsapp.domain.goods.repository.GoodsOrderItemRepository
import com.sportsapp.domain.goods.repository.GoodsOrderRepository
import com.sportsapp.domain.goods.repository.StockRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal

class BE11CancelPendingOrderPropagationTest : BehaviorSpec({

    fun buildService(
        goodsOrderRepository: GoodsOrderRepository,
        goodsOrderItemRepository: GoodsOrderItemRepository,
        stockRepository: StockRepository,
    ) = GoodsDomainService(
        productRepository = mockk(),
        stockRepository = stockRepository,
        productCustomRepository = mockk(),
        popularProductsCache = mockk(),
        goodsOrderRepository = goodsOrderRepository,
        goodsOrderItemRepository = goodsOrderItemRepository,
        goodsOrderCustomRepository = mockk(),
        limitedDropRepository = mockk(),
    )

    Given("[S-01] PENDING 주문 취소 시 GoodsOrderItem이 soft-delete된다") {
        val goodsOrderRepository = mockk<GoodsOrderRepository>()
        val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = buildService(goodsOrderRepository, goodsOrderItemRepository, stockRepository)

        val order = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("10000"))
        val item = GoodsOrderItem(orderId = order.id, productId = 10L, quantity = 2, unitPrice = BigDecimal("5000"))
        val stock = testStock(quantity = 5)

        every { goodsOrderRepository.findById(1L) } returns order
        every { goodsOrderRepository.save(any()) } returns order
        every { goodsOrderItemRepository.findByOrderId(1L) } returns listOf(item)
        every { stockRepository.findByProductId(10L) } returns stock
        every { stockRepository.save(any()) } returns stock
        val savedItems = slot<List<GoodsOrderItem>>()
        every { goodsOrderItemRepository.saveAll(capture(savedItems)) } returns emptyList()

        When("[S-01] cancelPendingOrder를 호출하면") {
            service.cancelPendingOrder(1L)

            Then("[S-01] GoodsOrder가 CANCELLED가 되고 item이 soft-delete된 채로 saveAll이 호출된다") {
                order.status shouldBe GoodsOrderStatus.CANCELLED
                verify(exactly = 1) { goodsOrderItemRepository.saveAll(any()) }
                savedItems.captured.size shouldBe 1
                savedItems.captured.first().isDeleted shouldBe true
            }
        }
    }

    Given("[S-04] 이미 CANCELLED 상태인 orderId로 cancelPendingOrder를 재호출하면") {
        val goodsOrderRepository = mockk<GoodsOrderRepository>()
        val goodsOrderItemRepository = mockk<GoodsOrderItemRepository>()
        val stockRepository = mockk<StockRepository>()
        val service = buildService(goodsOrderRepository, goodsOrderItemRepository, stockRepository)

        val cancelledOrder = GoodsOrder.create(userId = 1L, totalAmount = BigDecimal("5000"))
        cancelledOrder.cancel()

        every { goodsOrderRepository.findById(2L) } returns cancelledOrder

        When("[S-04] cancelPendingOrder를 다시 호출하면") {
            Then("[S-04] InvalidGoodsOrderStateException이 발생하고 아이템 삭제는 일어나지 않는다") {
                shouldThrow<InvalidGoodsOrderStateException> {
                    service.cancelPendingOrder(2L)
                }
                verify(exactly = 0) { goodsOrderItemRepository.saveAll(any()) }
            }
        }
    }
})
