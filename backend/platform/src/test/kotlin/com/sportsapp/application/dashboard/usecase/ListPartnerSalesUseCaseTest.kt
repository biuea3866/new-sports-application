package com.sportsapp.application.dashboard.usecase

import com.sportsapp.application.dashboard.dto.ListPartnerSalesCommand
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderRef
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.payment.entity.Payment
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentDomainService
import com.sportsapp.domain.payment.vo.PaymentMethod
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 포털 "매출·결제 내역" — 파트너가 **판매한** 건의 결제를 모아 보여준다.
 *
 * 기존 화면은 `/payments/me`(구매자 스코프)를 호출해 파트너 본인 결제만 찾았고, 실제 결제
 * 22건이 전부 구매자 명의라 항상 0건이었다. payment는 판매자를 모르므로 각 주문 컨텍스트가
 * 자기 주문 id를 답하고, 이 UseCase가 그 참조로 payment에 결제를 되묻는다.
 */
class ListPartnerSalesUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val ticketingDomainService = mockk<TicketingDomainService>()
    val goodsDomainService = mockk<GoodsDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = ListPartnerSalesUseCase(
        bookingDomainService,
        ticketingDomainService,
        goodsDomainService,
        paymentDomainService,
    )

    val partnerUserId = 69L
    val pageable = PageRequest.of(0, 20)
    val command = ListPartnerSalesCommand(
        ownerUserId = partnerUserId,
        status = null,
        paidAtFrom = null,
        paidAtTo = null,
        pageable = pageable,
    )

    fun stubPayment(id: Long, orderType: OrderType, orderId: Long, amount: String) = mockk<Payment>(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.orderType } returns orderType
        every { this@mockk.orderId } returns orderId
        every { this@mockk.amount } returns BigDecimal(amount)
        every { status } returns PaymentStatus.COMPLETED
        every { method } returns PaymentMethod.CREDIT_CARD
        every { paidAt } returns ZonedDateTime.now()
        every { pgTransactionId } returns "tid-$id"
        every { provider } returns "TOSS"
    }

    Given("예약·티켓·굿즈 매출이 모두 있는 파트너") {
        every { bookingDomainService.findBookingIdsForFacilityOwner(partnerUserId) } returns listOf(1L, 2L)
        every { ticketingDomainService.findTicketOrderIdsForEventOwner(partnerUserId) } returns listOf(2L)
        every { goodsDomainService.findGoodsOrderSellerAmounts(partnerUserId) } returns
            mapOf(2L to BigDecimal("158000.00"))
        val orderRefsSlot = slot<List<OrderRef>>()
        every {
            paymentDomainService.findSalesByOrderRefs(capture(orderRefsSlot), null, null, null, pageable)
        } returns PageImpl(
            listOf(
                stubPayment(16L, OrderType.BOOKING, 1L, "25000.00"),
                stubPayment(22L, OrderType.TICKETING, 2L, "88000.00"),
                stubPayment(15L, OrderType.GOODS, 2L, "158000.00"),
            ),
            pageable,
            3L,
        )

        When("매출 내역을 조회하면") {
            val result = useCase.execute(command)

            Then("세 컨텍스트의 주문 참조가 유형과 함께 payment에 전달된다") {
                orderRefsSlot.captured shouldContainExactlyInAnyOrder listOf(
                    OrderRef(OrderType.BOOKING, 1L),
                    OrderRef(OrderType.BOOKING, 2L),
                    OrderRef(OrderType.TICKETING, 2L),
                    OrderRef(OrderType.GOODS, 2L),
                )
            }

            Then("결제 행이 모두 반환된다") {
                result.totalElements shouldBe 3L
                result.sales.map { it.paymentId } shouldContainExactlyInAnyOrder listOf(16L, 22L, 15L)
            }

            // 예약·티켓은 판매자가 단일이라 결제 총액이 곧 내 매출이다.
            Then("단일 판매자 주문은 결제 총액이 그대로 내 매출이 된다") {
                val bookingSale = result.sales.first { it.paymentId == 16L }
                bookingSale.amount shouldBe BigDecimal("25000.00")
                bookingSale.sellerAmount shouldBe BigDecimal("25000.00")
            }

            Then("굿즈는 내 상품 항목 합계가 내 매출로 실린다") {
                val goodsSale = result.sales.first { it.paymentId == 15L }
                goodsSale.sellerAmount shouldBe BigDecimal("158000.00")
            }
        }
    }

    Given("굿즈 주문에 다른 판매자 상품이 섞여 있을 때") {
        every { bookingDomainService.findBookingIdsForFacilityOwner(partnerUserId) } returns emptyList()
        every { ticketingDomainService.findTicketOrderIdsForEventOwner(partnerUserId) } returns emptyList()
        // 결제 총액 100,000 중 내 상품 몫은 30,000뿐인 혼합 주문.
        every { goodsDomainService.findGoodsOrderSellerAmounts(partnerUserId) } returns
            mapOf(7L to BigDecimal("30000.00"))
        every {
            paymentDomainService.findSalesByOrderRefs(any(), null, null, null, pageable)
        } returns PageImpl(listOf(stubPayment(30L, OrderType.GOODS, 7L, "100000.00")), pageable, 1L)

        When("매출 내역을 조회하면") {
            val result = useCase.execute(command)

            // 결제 총액을 그대로 매출로 계상하면 남의 매출까지 내 것이 된다.
            Then("결제 총액과 내 매출이 구분돼 실린다") {
                val sale = result.sales.first()
                sale.amount shouldBe BigDecimal("100000.00")
                sale.sellerAmount shouldBe BigDecimal("30000.00")
            }
        }
    }

    Given("판매한 주문이 하나도 없는 파트너") {
        // 호출 0회를 검증하려면 이 시나리오 전용 mock이 필요하다 —
        // spec 상단 mock은 앞선 Given들이 이미 사용해 호출 이력이 남아 있다.
        val emptyBookingDomainService = mockk<BookingDomainService>()
        val emptyTicketingDomainService = mockk<TicketingDomainService>()
        val emptyGoodsDomainService = mockk<GoodsDomainService>()
        val untouchedPaymentDomainService = mockk<PaymentDomainService>()
        val emptyUseCase = ListPartnerSalesUseCase(
            emptyBookingDomainService,
            emptyTicketingDomainService,
            emptyGoodsDomainService,
            untouchedPaymentDomainService,
        )
        every { emptyBookingDomainService.findBookingIdsForFacilityOwner(partnerUserId) } returns emptyList()
        every { emptyTicketingDomainService.findTicketOrderIdsForEventOwner(partnerUserId) } returns emptyList()
        every { emptyGoodsDomainService.findGoodsOrderSellerAmounts(partnerUserId) } returns emptyMap()

        When("매출 내역을 조회하면") {
            val result = emptyUseCase.execute(command)

            Then("빈 목록이 반환된다") {
                result.totalElements shouldBe 0L
                result.sales.shouldBeEmpty()
            }

            // 참조가 비었는데 payment를 조건 없이 조회하면 전체 결제가 노출된다 — 권한 누수.
            Then("결제 조회 자체를 하지 않는다") {
                verify(exactly = 0) {
                    untouchedPaymentDomainService.findSalesByOrderRefs(any(), any(), any(), any(), any())
                }
            }
        }
    }

    Given("상태·기간 필터를 지정했을 때") {
        val from = ZonedDateTime.now().minusDays(7)
        val to = ZonedDateTime.now()
        val filtered = ListPartnerSalesCommand(
            ownerUserId = partnerUserId,
            status = PaymentStatus.COMPLETED,
            paidAtFrom = from,
            paidAtTo = to,
            pageable = pageable,
        )
        every { bookingDomainService.findBookingIdsForFacilityOwner(partnerUserId) } returns listOf(1L)
        every { ticketingDomainService.findTicketOrderIdsForEventOwner(partnerUserId) } returns emptyList()
        every { goodsDomainService.findGoodsOrderSellerAmounts(partnerUserId) } returns emptyMap()
        every {
            paymentDomainService.findSalesByOrderRefs(any(), PaymentStatus.COMPLETED, from, to, pageable)
        } returns PageImpl(emptyList(), pageable, 0L)

        When("매출 내역을 조회하면") {
            useCase.execute(filtered)

            Then("필터가 그대로 payment 조회에 전달된다") {
                verify(exactly = 1) {
                    paymentDomainService.findSalesByOrderRefs(any(), PaymentStatus.COMPLETED, from, to, pageable)
                }
            }
        }
    }
})
