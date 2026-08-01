package com.sportsapp.infrastructure.order

import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

private fun goodsOrderMock(
    id: Long,
    status: GoodsOrderStatus,
    paymentId: Long?,
    createdAt: ZonedDateTime,
): GoodsOrder = mockk(relaxed = true) {
    every { this@mockk.id } returns id
    every { this@mockk.status } returns status
    every { this@mockk.paymentId } returns paymentId
    every { this@mockk.createdAt } returns createdAt
}

/**
 * S2-01 — [LocalOrderHistoryAdapter]가 [com.sportsapp.domain.order.gateway.OrderHistoryGateway]
 * 계약을 4개 코어 DomainService 호출 + [com.sportsapp.domain.order.dto.OrderHistoryItem] 매핑으로
 * 만족시키는지 검증한다. fan-out·타임아웃·부분 저하는 edge의 `OrderCompositionServiceTest`가
 * 검증하므로 여기서는 순수 매핑 정확성(이동 전 `OrderCompositionServiceTest`의 매핑 케이스를
 * 그대로 승계)만 다룬다.
 */
class LocalOrderHistoryAdapterTest : BehaviorSpec({

    val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)
    val userId = 1L

    Given("booking 도메인에 예약 이력이 있는 상황") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(userId) } returns listOf(
            BookingOrderItem(
                bookingId = 10L, slotId = 100L, userId = userId,
                status = BookingStatus.EXPIRED, paymentId = null,
                title = "2026-06-01 09:00-10:00 시설 예약", createdAt = now,
            ),
        )

        val adapter = LocalOrderHistoryAdapter(
            bookingDomainService = bookingDomainService,
            goodsDomainService = mockk(),
            ticketingDomainService = mockk(),
            recruitmentDomainService = mockk(),
        )

        When("findBookingOrders를 호출하면") {
            val items = adapter.findBookingOrders(userId)

            Then("BOOKING 타입 OrderHistoryItem으로 매핑된다") {
                val item = items.single()
                item.orderType shouldBe OrderType.BOOKING
                item.sourceId shouldBe 10L
                item.title shouldBe "2026-06-01 09:00-10:00 시설 예약"
                item.status shouldBe "EXPIRED"
                item.paymentId shouldBe null
                item.detailPath shouldBe "/bookings/10"
            }
        }
    }

    Given("goods 도메인에 주문 이력이 있는 상황") {
        val goodsDomainService = mockk<GoodsDomainService>()
        val pageable = PageRequest.of(0, 20)
        every { goodsDomainService.listMyOrdersWithTitle(userId, pageable) } returns PageImpl(
            listOf(
                GoodsOrderWithTitle(
                    order = goodsOrderMock(id = 20L, status = GoodsOrderStatus.SHIPPED, paymentId = 200L, createdAt = now),
                    title = "요가매트 프리미엄 외 1건",
                ),
            ),
        )

        val adapter = LocalOrderHistoryAdapter(
            bookingDomainService = mockk(),
            goodsDomainService = goodsDomainService,
            ticketingDomainService = mockk(),
            recruitmentDomainService = mockk(),
        )

        When("findGoodsOrders를 호출하면") {
            val items = adapter.findGoodsOrders(userId, pageable)

            Then("GOODS 타입 OrderHistoryItem으로 매핑되고 title은 각 컨텍스트가 만든 값 그대로다") {
                val item = items.single()
                item.orderType shouldBe OrderType.GOODS
                item.sourceId shouldBe 20L
                item.title shouldBe "요가매트 프리미엄 외 1건"
                item.status shouldBe "SHIPPED"
                item.paymentId shouldBe 200L
                item.detailPath shouldBe "/goods-orders/20"
            }
        }
    }

    Given("ticketing 도메인에 주문 이력이 있는 상황") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 30L, status = OrderStatus.CONFIRMED, eventTitle = "Concert Dec",
                paymentId = 300L, createdAt = now,
            ),
        )

        val adapter = LocalOrderHistoryAdapter(
            bookingDomainService = mockk(),
            goodsDomainService = mockk(),
            ticketingDomainService = ticketingDomainService,
            recruitmentDomainService = mockk(),
        )

        When("findTicketingOrders를 호출하면") {
            val items = adapter.findTicketingOrders(userId)

            Then("TICKETING 타입 OrderHistoryItem으로 매핑된다") {
                val item = items.single()
                item.orderType shouldBe OrderType.TICKETING
                item.sourceId shouldBe 30L
                item.title shouldBe "Concert Dec"
                item.detailPath shouldBe "/ticket-orders/30"
            }
        }
    }

    Given("recruitment 도메인에 신청 이력이 있는 상황") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns listOf(
            ApplicationWithRecruitmentTitle(
                applicationId = 40L, status = ApplicationStatus.REFUNDED, recruitmentTitle = "주말 축구 모임",
                paymentId = 400L, createdAt = now,
            ),
        )

        val adapter = LocalOrderHistoryAdapter(
            bookingDomainService = mockk(),
            goodsDomainService = mockk(),
            ticketingDomainService = mockk(),
            recruitmentDomainService = recruitmentDomainService,
        )

        When("findRecruitmentOrders를 호출하면") {
            val items = adapter.findRecruitmentOrders(userId)

            Then("RECRUITMENT 타입 OrderHistoryItem으로 매핑된다") {
                val item = items.single()
                item.orderType shouldBe OrderType.RECRUITMENT
                item.sourceId shouldBe 40L
                item.title shouldBe "주말 축구 모임"
                item.detailPath shouldBe "/applications/40"
            }
        }
    }
})
