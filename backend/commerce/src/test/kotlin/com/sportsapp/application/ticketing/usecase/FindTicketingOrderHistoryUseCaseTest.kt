package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.dto.TicketOrderWithEventTitle
import com.sportsapp.domain.ticketing.dto.TicketSeatLabel
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

private val CREATED_AT: ZonedDateTime = ZonedDateTime.of(2026, 6, 2, 9, 0, 0, 0, ZoneOffset.UTC)

/**
 * 통합 주문내역(BE-08)의 `OrderHistoryGateway.findTicketingOrders` 원격 구현(2단계) 공급자 (S2-03).
 *
 * 좌석은 **원본 필드(section·rowNo·seatNo)로 실어 보낸다** — 문자열로 미리 조합하지 않는다.
 * 모바일이 이미 티켓 구매 확인 화면에서 쓰는 `formatSeatDescription` 과 서식이 어긋나는 두 번째
 * 포맷터가 생기지 않게 하는 것이 목적이다. 같은 경기의 여러 좌석 주문을 사용자가 구분하는 근거다.
 */
class FindTicketingOrderHistoryUseCaseTest : BehaviorSpec({

    Given("좌석 2석을 예매한 본인 주문") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.listTicketOrdersBy(7L) } returns listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 21L,
                status = OrderStatus.CONFIRMED,
                eventTitle = "농구 결승전",
                paymentId = 801L,
                createdAt = CREATED_AT,
                totalAmount = BigDecimal("88000"),
                seats = listOf(
                    TicketSeatLabel(section = "R", rowNo = "1", seatNo = "R-01"),
                    TicketSeatLabel(section = "R", rowNo = "1", seatNo = "R-02"),
                ),
            ),
        )
        val useCase = FindTicketingOrderHistoryUseCase(ticketingDomainService)

        When("execute(userId=7) 를 호출하면") {
            val result = useCase.execute(7L)

            Then("계약 필드만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result[0].sourceId shouldBe 21L
                result[0].title shouldBe "농구 결승전"
                result[0].status shouldBe OrderStatus.CONFIRMED
                result[0].paymentId shouldBe 801L
                result[0].createdAt shouldBe CREATED_AT
                result[0].amount shouldBe BigDecimal("88000")
            }

            Then("좌석을 원본 필드 목록으로 실어 보낸다 (문자열 미조합)") {
                result[0].seats.map { Triple(it.section, it.rowNo, it.seatNo) } shouldBe listOf(
                    Triple("R", "1", "R-01"),
                    Triple("R", "1", "R-02"),
                )
            }

            Then("공급자는 orderType·detailPath 를 만들지 않는다 (파사드 책임)") {
                val fieldNames = result[0]::class.members.map { it.name }
                fieldNames.contains("orderType") shouldBe false
                fieldNames.contains("detailPath") shouldBe false
            }
        }
    }

    Given("좌석 정보가 없는 주문") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.listTicketOrdersBy(7L) } returns listOf(
            TicketOrderWithEventTitle(
                ticketOrderId = 22L,
                status = OrderStatus.PENDING,
                eventTitle = "배구 개막전",
                paymentId = null,
                createdAt = CREATED_AT,
                totalAmount = BigDecimal("30000"),
                seats = emptyList(),
            ),
        )
        val useCase = FindTicketingOrderHistoryUseCase(ticketingDomainService)

        When("execute 를 호출하면") {
            val result = useCase.execute(7L)

            Then("좌석을 빈 목록으로 공급한다 — null 판정(구분 정보 없음)은 파사드가 한다") {
                result[0].seats.shouldBeEmpty()
            }

            Then("결제 전 주문은 paymentId 가 null 이다") {
                result[0].paymentId shouldBe null
            }
        }
    }

    Given("주문이 없는 사용자") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        every { ticketingDomainService.listTicketOrdersBy(999L) } returns emptyList()
        val useCase = FindTicketingOrderHistoryUseCase(ticketingDomainService)

        When("execute(userId=999) 를 호출하면") {
            val result = useCase.execute(999L)

            Then("요청한 사용자 id 만 도메인에 전달하고 빈 목록을 반환한다") {
                result.shouldBeEmpty()
                verify(exactly = 1) { ticketingDomainService.listTicketOrdersBy(999L) }
            }
        }
    }
})
