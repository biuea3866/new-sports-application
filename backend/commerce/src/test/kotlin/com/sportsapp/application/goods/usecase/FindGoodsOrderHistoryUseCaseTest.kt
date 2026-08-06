package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryCriteria
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private val CREATED_AT: ZonedDateTime = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)

/**
 * 통합 주문내역(BE-08)의 `OrderHistoryGateway.findGoodsOrders` 원격 구현(2단계) 공급자 (S2-03).
 * 개인 데이터라 요청자 본인 주문만 반환한다 — 소유권 경계는 DomainService 조회가 보장한다.
 */
class FindGoodsOrderHistoryUseCaseTest : BehaviorSpec({

    fun order(id: Long, paymentId: Long?, amount: BigDecimal): GoodsOrder = mockk(relaxed = true) {
        every { this@mockk.id } returns id
        every { status } returns GoodsOrderStatus.CONFIRMED
        every { this@mockk.paymentId } returns paymentId
        every { totalAmount } returns amount
        every { createdAt } returns CREATED_AT
    }

    Given("본인 굿즈 주문이 있는 사용자") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { goodsDomainService.listMyOrdersWithTitle(7L, pageable) } returns PageImpl(
            listOf(
                GoodsOrderWithTitle(
                    order = order(id = 11L, paymentId = 701L, amount = BigDecimal("59000")),
                    title = "러닝화 외 1건",
                ),
            ),
            pageable,
            1,
        )
        val useCase = FindGoodsOrderHistoryUseCase(goodsDomainService)

        When("execute(userId=7, page=0, size=20) 를 호출하면") {
            val result = useCase.execute(InternalGoodsOrderHistoryCriteria(userId = 7L, page = 0, size = 20))

            Then("계약 필드만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result[0].sourceId shouldBe 11L
                result[0].title shouldBe "러닝화 외 1건"
                result[0].status shouldBe GoodsOrderStatus.CONFIRMED
                result[0].paymentId shouldBe 701L
                result[0].createdAt shouldBe CREATED_AT
            }

            Then("결제 금액을 함께 공급한다 — edge 가 payment 를 역참조하지 않는 근거다") {
                result[0].amount shouldBe BigDecimal("59000")
            }

            Then("공급자는 orderType·detailPath 를 만들지 않는다 (파사드 책임)") {
                val fieldNames = result[0]::class.members.map { it.name }
                fieldNames.contains("orderType") shouldBe false
                fieldNames.contains("detailPath") shouldBe false
            }
        }
    }

    Given("결제 전(미결제) 주문") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { goodsDomainService.listMyOrdersWithTitle(7L, pageable) } returns PageImpl(
            listOf(
                GoodsOrderWithTitle(
                    order = order(id = 12L, paymentId = null, amount = BigDecimal("30000")),
                    title = "장바구니 주문",
                ),
            ),
            pageable,
            1,
        )
        val useCase = FindGoodsOrderHistoryUseCase(goodsDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(InternalGoodsOrderHistoryCriteria(userId = 7L, page = 0, size = 20))

            Then("paymentId 를 null 로 공급한다 — 파사드의 미결제 판정 입력이다") {
                result[0].paymentId shouldBe null
            }
        }
    }

    Given("다른 사용자 id 로 조회하면") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { goodsDomainService.listMyOrdersWithTitle(999L, pageable) } returns PageImpl(emptyList(), pageable, 0)
        val useCase = FindGoodsOrderHistoryUseCase(goodsDomainService)

        When("execute(userId=999) 를 호출하면") {
            val result = useCase.execute(InternalGoodsOrderHistoryCriteria(userId = 999L, page = 0, size = 20))

            Then("요청한 사용자 id 만 도메인에 전달하고 빈 목록을 반환한다") {
                result.shouldBeEmpty()
                verify(exactly = 1) { goodsDomainService.listMyOrdersWithTitle(999L, pageable) }
                verify(exactly = 0) { goodsDomainService.listMyOrdersWithTitle(7L, any()) }
            }
        }
    }
})
