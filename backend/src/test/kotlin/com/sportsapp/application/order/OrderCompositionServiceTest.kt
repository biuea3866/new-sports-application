package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.goods.entity.GoodsOrder
import com.sportsapp.domain.goods.entity.GoodsOrderStatus
import com.sportsapp.domain.goods.dto.GoodsOrderWithTitle
import com.sportsapp.domain.goods.service.GoodsDomainService
import com.sportsapp.domain.recruitment.dto.ApplicationWithRecruitmentTitle
import com.sportsapp.domain.recruitment.entity.ApplicationStatus
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val executor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4
    maxPoolSize = 4
    setThreadNamePrefix("order-history-test-")
    initialize()
}

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

class OrderCompositionServiceTest : BehaviorSpec({

    fun buildService(
        bookingDomainService: BookingDomainService = mockk(),
        goodsDomainService: GoodsDomainService = mockk(),
        ticketingDomainService: TicketingDomainService = mockk(),
        recruitmentDomainService: RecruitmentDomainService = mockk(),
    ) = OrderCompositionService(
        bookingDomainService = bookingDomainService,
        goodsDomainService = goodsDomainService,
        ticketingDomainService = ticketingDomainService,
        recruitmentDomainService = recruitmentDomainService,
        orderHistoryExecutor = executor,
    )

    fun emptyCriteria(page: Int = 0, size: Int = 20, orderType: OrderType? = null, status: String? = null) =
        OrderHistoryCriteria(orderType = orderType, status = status, page = page, size = size)

    Given("4개 도메인 모두 주문 이력이 있는 사용자") {
        val userId = 1L
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()

        val bookingCreatedAt = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val goodsCreatedAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
        val ticketingCreatedAt = ZonedDateTime.of(2026, 6, 3, 9, 0, 0, 0, ZoneOffset.UTC)
        val recruitmentCreatedAt = ZonedDateTime.of(2026, 6, 4, 9, 0, 0, 0, ZoneOffset.UTC)

        every { bookingDomainService.findOrderHistory(userId) } returns listOf(
            BookingOrderItem(
                bookingId = 10L, slotId = 100L, userId = userId,
                status = BookingStatus.EXPIRED, paymentId = null,
                title = "2026-06-01 09:00-10:00 시설 예약", createdAt = bookingCreatedAt,
            ),
        )
        every { goodsDomainService.listMyOrdersWithTitle(userId, any()) } returns PageImpl(
            listOf(
                GoodsOrderWithTitle(
                    order = goodsOrderMock(id = 20L, status = GoodsOrderStatus.SHIPPED, paymentId = 200L, createdAt = goodsCreatedAt),
                    title = "요가매트 프리미엄 외 1건",
                ),
            ),
            PageRequest.of(0, 20),
            1L,
        )
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 30L, status = OrderStatus.CONFIRMED, eventTitle = "Concert Dec",
                paymentId = 300L, createdAt = ticketingCreatedAt,
            ),
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns listOf(
            ApplicationWithRecruitmentTitle(
                applicationId = 40L, status = ApplicationStatus.REFUNDED, recruitmentTitle = "주말 축구 모임",
                paymentId = 400L, createdAt = recruitmentCreatedAt,
            ),
        )

        val service = buildService(bookingDomainService, goodsDomainService, ticketingDomainService, recruitmentDomainService)

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("4개 도메인 주문이 createdAt 최신순 단일 응답으로 조합된다") {
                result.items.map { it.orderType } shouldContainExactly listOf(
                    OrderType.RECRUITMENT, OrderType.TICKETING, OrderType.GOODS, OrderType.BOOKING,
                )
                result.failedDomains.shouldBeEmpty()
            }

            Then("각 항목이 각 주문 컨텍스트가 만든 사람이 읽는 title을 그대로 노출한다") {
                val byType = result.items.associateBy { it.orderType }
                byType.getValue(OrderType.GOODS).title shouldBe "요가매트 프리미엄 외 1건"
                byType.getValue(OrderType.TICKETING).title shouldBe "Concert Dec"
                byType.getValue(OrderType.RECRUITMENT).title shouldBe "주말 축구 모임"
                byType.getValue(OrderType.BOOKING).title shouldBe "2026-06-01 09:00-10:00 시설 예약"
            }

            Then("각 항목의 status는 각 도메인 자신의 enum name 그대로 노출된다") {
                val byType = result.items.associateBy { it.orderType }
                byType.getValue(OrderType.GOODS).status shouldBe "SHIPPED"
                byType.getValue(OrderType.TICKETING).status shouldBe "CONFIRMED"
                byType.getValue(OrderType.RECRUITMENT).status shouldBe "REFUNDED"
                byType.getValue(OrderType.BOOKING).status shouldBe "EXPIRED"
            }

            Then("각 항목에 paymentId 연계가 노출된다") {
                val byType = result.items.associateBy { it.orderType }
                byType.getValue(OrderType.GOODS).paymentId shouldBe 200L
                byType.getValue(OrderType.TICKETING).paymentId shouldBe 300L
                byType.getValue(OrderType.RECRUITMENT).paymentId shouldBe 400L
                byType.getValue(OrderType.BOOKING).paymentId shouldBe null
            }
        }
    }

    Given("goods 도메인 조회가 300ms 타임아웃을 초과할 때") {
        val userId = 2L
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()

        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        every { bookingDomainService.findOrderHistory(userId) } returns listOf(
            BookingOrderItem(
                bookingId = 11L, slotId = 101L, userId = userId,
                status = BookingStatus.CONFIRMED, paymentId = 111L,
                title = "예약 라벨", createdAt = now,
            ),
        )
        every { goodsDomainService.listMyOrdersWithTitle(userId, any()) } answers {
            Thread.sleep(500)
            PageImpl(emptyList(), PageRequest.of(0, 20), 0L)
        }
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 31L, status = OrderStatus.CONFIRMED, eventTitle = "Concert",
                paymentId = 311L, createdAt = now,
            ),
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns listOf(
            ApplicationWithRecruitmentTitle(
                applicationId = 41L, status = ApplicationStatus.CONFIRMED, recruitmentTitle = "모임",
                paymentId = 411L, createdAt = now,
            ),
        )

        val service = buildService(bookingDomainService, goodsDomainService, ticketingDomainService, recruitmentDomainService)

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("나머지 3개 도메인 결과를 반환하고 goods를 failedDomains에 표기한다 (FR-11)") {
                result.failedDomains shouldContainExactly listOf(OrderType.GOODS)
                result.items.map { it.orderType }.toSet() shouldBe setOf(OrderType.BOOKING, OrderType.TICKETING, OrderType.RECRUITMENT)
            }
        }
    }

    Given("orderType=TICKETING 필터가 주어졌을 때") {
        val userId = 3L
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { bookingDomainService.findOrderHistory(userId) } returns listOf(
            BookingOrderItem(bookingId = 12L, slotId = 102L, userId = userId, status = BookingStatus.CONFIRMED, paymentId = 1L, title = "예약", createdAt = now),
        )
        every { goodsDomainService.listMyOrdersWithTitle(userId, any()) } returns PageImpl(
            listOf(GoodsOrderWithTitle(order = goodsOrderMock(21L, GoodsOrderStatus.CONFIRMED, 2L, now), title = "상품")),
            PageRequest.of(0, 20), 1L,
        )
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns listOf(
            TicketOrderWithEventTitle(ticketOrderId = 32L, status = OrderStatus.CONFIRMED, eventTitle = "티켓", paymentId = 3L, createdAt = now),
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns listOf(
            ApplicationWithRecruitmentTitle(applicationId = 42L, status = ApplicationStatus.CONFIRMED, recruitmentTitle = "모집", paymentId = 4L, createdAt = now),
        )

        val service = buildService(bookingDomainService, goodsDomainService, ticketingDomainService, recruitmentDomainService)

        When("history(userId, orderType=TICKETING)를 호출하면") {
            val result = service.history(userId, emptyCriteria(orderType = OrderType.TICKETING))

            Then("TICKETING 주문만 조회된다") {
                result.items.map { it.orderType } shouldContainExactly listOf(OrderType.TICKETING)
                result.items.first().sourceId shouldBe 32L
            }
        }
    }

    Given("status=CANCELLED 필터가 주어졌을 때") {
        val userId = 4L
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { bookingDomainService.findOrderHistory(userId) } returns listOf(
            BookingOrderItem(bookingId = 13L, slotId = 103L, userId = userId, status = BookingStatus.CANCELLED, paymentId = null, title = "예약", createdAt = now),
        )
        every { goodsDomainService.listMyOrdersWithTitle(userId, any()) } returns PageImpl(
            listOf(GoodsOrderWithTitle(order = goodsOrderMock(22L, GoodsOrderStatus.CANCELLED, null, now), title = "상품")),
            PageRequest.of(0, 20), 1L,
        )
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns listOf(
            TicketOrderWithEventTitle(ticketOrderId = 33L, status = OrderStatus.CANCELLED, eventTitle = "티켓", paymentId = null, createdAt = now),
        )
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns listOf(
            ApplicationWithRecruitmentTitle(applicationId = 43L, status = ApplicationStatus.CANCELLED, recruitmentTitle = "모집", paymentId = null, createdAt = now),
        )

        val service = buildService(bookingDomainService, goodsDomainService, ticketingDomainService, recruitmentDomainService)

        When("history(userId, status=CANCELLED)를 호출하면") {
            val result = service.history(userId, emptyCriteria(status = "CANCELLED"))

            Then("4개 도메인 모두 CANCELLED 상태 주문만 반환된다 (도메인별 status 필터 매핑 검증)") {
                result.items.map { it.orderType }.toSet() shouldBe setOf(
                    OrderType.BOOKING, OrderType.GOODS, OrderType.TICKETING, OrderType.RECRUITMENT,
                )
                result.items.forAll { it.status shouldBe "CANCELLED" }
            }
        }
    }

    Given("주문 이력이 하나도 없는 사용자") {
        val userId = 5L
        val bookingDomainService = mockk<BookingDomainService>()
        val goodsDomainService = mockk<GoodsDomainService>()
        val ticketingDomainService = mockk<TicketingDomainService>()
        val recruitmentDomainService = mockk<RecruitmentDomainService>()

        every { bookingDomainService.findOrderHistory(userId) } returns emptyList()
        every { goodsDomainService.listMyOrdersWithTitle(userId, any()) } returns PageImpl(emptyList(), PageRequest.of(0, 20), 0L)
        every { ticketingDomainService.listTicketOrdersBy(userId) } returns emptyList()
        every { recruitmentDomainService.listApplicationsWithTitleBy(userId) } returns emptyList()

        val service = buildService(bookingDomainService, goodsDomainService, ticketingDomainService, recruitmentDomainService)

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("빈 items를 반환한다 (엣지)") {
                result.items.shouldBeEmpty()
                result.failedDomains.shouldBeEmpty()
            }
        }
    }
})
