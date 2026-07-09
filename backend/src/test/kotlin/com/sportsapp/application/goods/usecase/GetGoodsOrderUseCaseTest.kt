package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.dto.GoodsOrderDetail
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderItem
import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal

class GetGoodsOrderUseCaseTest : BehaviorSpec({

    val goodsDomainService = mockk<GoodsDomainService>()
    val useCase = GetGoodsOrderUseCase(goodsDomainService)

    Given("본인 소유 주문 상세를 조회하는 상황") {
        val userId = 1L
        val orderId = 10L
        val order = GoodsOrder.create(userId = userId, totalAmount = BigDecimal("89000"))
        val item = GoodsOrderItem(orderId = orderId, productId = 5L, quantity = 1, unitPrice = BigDecimal("89000"))
        val detail = GoodsOrderDetail(order = order, items = listOf(item), title = "나이키 러닝화")

        every { goodsDomainService.getOrder(userId, orderId) } returns detail

        When("execute를 호출하면") {
            val result = useCase.execute(userId, orderId)

            Then("GoodsDomainService.getOrder 결과(대표 상품명 포함)를 그대로 반환한다") {
                result shouldBe detail
                result.title shouldBe "나이키 러닝화"
                verify(exactly = 1) { goodsDomainService.getOrder(userId, orderId) }
            }
        }
    }
})
