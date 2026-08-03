package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.order.dto.OrderHistoryItem
import com.sportsapp.domain.order.gateway.OrderHistoryGateway
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.core.task.AsyncTaskExecutor
import org.springframework.core.task.TaskRejectedException
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val executor = ThreadPoolTaskExecutor().apply {
    corePoolSize = 4
    maxPoolSize = 4
    setThreadNamePrefix("order-history-test-")
    initialize()
}

/**
 * 첫 제출만 동기 실행으로 통과시키고, 이후 제출은 `TaskRejectedException`을 동기로 던지는
 * fake executor — bounded executor 포화 시나리오를 실 스레드풀 타이밍 레이스 없이 재현한다.
 */
private class SaturatedAfterFirstSubmissionExecutor : AsyncTaskExecutor {
    private var submissionCount = 0

    override fun execute(task: Runnable) {
        submissionCount += 1
        if (submissionCount > 1) {
            throw TaskRejectedException("order-history-test: simulated executor saturation")
        }
        task.run()
    }
}

private fun orderHistoryItem(
    orderType: OrderType,
    sourceId: Long,
    title: String,
    status: String,
    paymentId: Long?,
    createdAt: ZonedDateTime,
) = OrderHistoryItem(
    orderType = orderType,
    sourceId = sourceId,
    title = title,
    status = status,
    paymentId = paymentId,
    detailPath = "/orders/$sourceId",
    createdAt = createdAt,
    // 이 스펙은 fan-out·타임아웃·정렬·페이지네이션 조합 로직만 검증한다(매핑 정확성은
    // LocalOrderHistoryAdapterTest가 담당) — amount는 테스트 관심사 밖이라 null 고정.
    amount = null,
)

/**
 * S2-01 — [OrderCompositionService]는 이제 4개 코어 DomainService가 아니라 edge 소유
 * [OrderHistoryGateway] 하나만 주입받는다. fan-out·타임아웃·부분 저하·페이지네이션 로직은
 * 이동 전과 동일해야 하므로(동작 변화 0), 여기서는 Gateway를 mock으로 대체해 같은 시나리오를
 * 검증한다. 타 모듈 DTO → OrderHistoryItem 매핑 자체는 bootstrap의
 * `LocalOrderHistoryAdapterTest`가 검증한다.
 */
class OrderCompositionServiceTest : BehaviorSpec({

    fun buildService(orderHistoryGateway: OrderHistoryGateway = mockk()) = OrderCompositionService(
        orderHistoryGateway = orderHistoryGateway,
        orderHistoryExecutor = executor,
    )

    fun emptyCriteria(page: Int = 0, size: Int = 20, orderType: OrderType? = null, status: String? = null) =
        OrderHistoryCriteria(orderType = orderType, status = status, page = page, size = size)

    Given("4개 도메인 모두 주문 이력이 있는 사용자") {
        val userId = 1L
        val gateway = mockk<OrderHistoryGateway>()

        val bookingCreatedAt = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)
        val goodsCreatedAt = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)
        val ticketingCreatedAt = ZonedDateTime.of(2026, 6, 3, 9, 0, 0, 0, ZoneOffset.UTC)
        val recruitmentCreatedAt = ZonedDateTime.of(2026, 6, 4, 9, 0, 0, 0, ZoneOffset.UTC)

        every { gateway.findBookingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.BOOKING, 10L, "2026-06-01 09:00-10:00 시설 예약", "EXPIRED", null, bookingCreatedAt),
        )
        every { gateway.findGoodsOrders(userId, any()) } returns listOf(
            orderHistoryItem(OrderType.GOODS, 20L, "요가매트 프리미엄 외 1건", "SHIPPED", 200L, goodsCreatedAt),
        )
        every { gateway.findTicketingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.TICKETING, 30L, "Concert Dec", "CONFIRMED", 300L, ticketingCreatedAt),
        )
        every { gateway.findRecruitmentOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.RECRUITMENT, 40L, "주말 축구 모임", "REFUNDED", 400L, recruitmentCreatedAt),
        )

        val service = buildService(gateway)

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
        val gateway = mockk<OrderHistoryGateway>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { gateway.findBookingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.BOOKING, 11L, "예약 라벨", "CONFIRMED", 111L, now),
        )
        every { gateway.findGoodsOrders(userId, any()) } answers {
            Thread.sleep(500)
            emptyList()
        }
        every { gateway.findTicketingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.TICKETING, 31L, "Concert", "CONFIRMED", 311L, now),
        )
        every { gateway.findRecruitmentOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.RECRUITMENT, 41L, "모임", "CONFIRMED", 411L, now),
        )

        val service = buildService(gateway)

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("나머지 3개 도메인 결과를 반환하고 goods를 failedDomains에 표기한다 (FR-11)") {
                result.failedDomains shouldContainExactly listOf(OrderType.GOODS)
                result.items.map { it.orderType }.toSet() shouldBe setOf(OrderType.BOOKING, OrderType.TICKETING, OrderType.RECRUITMENT)
            }
        }
    }

    Given("orderHistoryExecutor가 포화 상태(코어+큐 가득)일 때") {
        val userId = 8L
        val gateway = mockk<OrderHistoryGateway>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { gateway.findBookingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.BOOKING, 14L, "예약", "CONFIRMED", 5L, now),
        )
        every { gateway.findGoodsOrders(userId, any()) } returns listOf(
            orderHistoryItem(OrderType.GOODS, 23L, "상품", "CONFIRMED", 6L, now),
        )
        every { gateway.findTicketingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.TICKETING, 34L, "티켓", "CONFIRMED", 7L, now),
        )
        every { gateway.findRecruitmentOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.RECRUITMENT, 44L, "모집", "CONFIRMED", 8L, now),
        )

        // 실 ThreadPoolTaskExecutor의 코어/큐 크기로 포화를 재현하면 소비 스레드와 제출 스레드 간
        // 타이밍 레이스(큐가 비워지는 순간 뒤이은 제출이 다시 성공)로 결과가 비결정적이다. 대신
        // 첫 제출만 통과시키고 이후는 `TaskRejectedException`을 동기로 던지는 결정적 fake executor를
        // 써서, `CompletableFuture.supplyAsync`가 `executor.execute()`를 제출 시점에 동기 호출한다는
        // 실제 결함 조건(코디네이터 지적)을 타이밍 무관하게 재현한다.
        val saturatedExecutor = SaturatedAfterFirstSubmissionExecutor()

        val service = OrderCompositionService(
            orderHistoryGateway = gateway,
            orderHistoryExecutor = saturatedExecutor,
        )

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("제출이 거부된 도메인은 failedDomains로 빠지고 나머지는 500 없이 정상 응답한다 (executor 포화, FR-11)") {
                result.failedDomains shouldContainExactlyInAnyOrder listOf(OrderType.GOODS, OrderType.TICKETING, OrderType.RECRUITMENT)
                result.items.map { it.orderType } shouldContainExactly listOf(OrderType.BOOKING)
            }
        }
    }

    Given("orderType=TICKETING 필터가 주어졌을 때") {
        val userId = 3L
        val gateway = mockk<OrderHistoryGateway>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { gateway.findBookingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.BOOKING, 12L, "예약", "CONFIRMED", 1L, now),
        )
        every { gateway.findGoodsOrders(userId, any()) } returns listOf(
            orderHistoryItem(OrderType.GOODS, 21L, "상품", "CONFIRMED", 2L, now),
        )
        every { gateway.findTicketingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.TICKETING, 32L, "티켓", "CONFIRMED", 3L, now),
        )
        every { gateway.findRecruitmentOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.RECRUITMENT, 42L, "모집", "CONFIRMED", 4L, now),
        )

        val service = buildService(gateway)

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
        val gateway = mockk<OrderHistoryGateway>()
        val now = ZonedDateTime.of(2026, 6, 1, 9, 0, 0, 0, ZoneOffset.UTC)

        every { gateway.findBookingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.BOOKING, 13L, "예약", "CANCELLED", null, now),
        )
        every { gateway.findGoodsOrders(userId, any()) } returns listOf(
            orderHistoryItem(OrderType.GOODS, 22L, "상품", "CANCELLED", null, now),
        )
        every { gateway.findTicketingOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.TICKETING, 33L, "티켓", "CANCELLED", null, now),
        )
        every { gateway.findRecruitmentOrders(userId) } returns listOf(
            orderHistoryItem(OrderType.RECRUITMENT, 43L, "모집", "CANCELLED", null, now),
        )

        val service = buildService(gateway)

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
        val gateway = mockk<OrderHistoryGateway>()

        every { gateway.findBookingOrders(userId) } returns emptyList()
        every { gateway.findGoodsOrders(userId, any()) } returns emptyList()
        every { gateway.findTicketingOrders(userId) } returns emptyList()
        every { gateway.findRecruitmentOrders(userId) } returns emptyList()

        val service = buildService(gateway)

        When("history(userId, 조건 없음)를 호출하면") {
            val result = service.history(userId, emptyCriteria())

            Then("빈 items를 반환한다 (엣지)") {
                result.items.shouldBeEmpty()
                result.failedDomains.shouldBeEmpty()
            }
        }
    }
})
