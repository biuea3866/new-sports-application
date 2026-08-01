package com.sportsapp.application.ticketing

import com.sportsapp.application.ticketing.usecase.ListMyEventsUseCase
import com.sportsapp.domain.ticketing.dto.EventWithSeatCounts
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 주최자 경기 목록 — 포털 "내 경기 목록" 카드가 `판매 {soldSeats} / {totalSeats}석`을 렌더하므로
 * 좌석 집계가 응답 계약의 일부다. 집계가 빠지면 화면에 "판매 / 석"만 남아 숫자가 통째로 사라진다.
 */
class ListMyEventsUseCaseTest : BehaviorSpec({

    val ticketingDomainService = mockk<TicketingDomainService>()
    val listMyEventsUseCase = ListMyEventsUseCase(ticketingDomainService)

    val ownerId = 7L
    val startsAt = ZonedDateTime.of(2026, 8, 21, 19, 0, 0, 0, ZoneOffset.UTC)

    fun buildEvent(id: Long, status: EventStatus) = Event(
        id = id,
        title = "전국 아마추어 풋살 결승 $id",
        venue = "한강 풋살파크",
        startsAt = startsAt,
        status = status,
        ownerId = ownerId,
    )

    Given("좌석 90석 중 2석이 팔린 경기를 보유한 주최자") {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(
            listOf(EventWithSeatCounts(event = buildEvent(1L, EventStatus.OPEN), totalSeats = 90L, soldSeats = 2L)),
            pageable,
            1L,
        )
        every { ticketingDomainService.findEventsWithSeatCountsByOwnerId(ownerId, pageable, null) } returns page

        When("내 경기 목록을 조회하면") {
            val result = listMyEventsUseCase.execute(ownerId, pageable, null)

            Then("총 좌석·판매 좌석·잔여 좌석이 응답에 실린다") {
                val myEvent = result.content.first()
                myEvent.totalSeats shouldBe 90L
                myEvent.soldSeats shouldBe 2L
                myEvent.availableSeats shouldBe 88L
            }

            Then("경기 기본 정보가 함께 실린다") {
                val myEvent = result.content.first()
                myEvent.id shouldBe 1L
                myEvent.venue shouldBe "한강 풋살파크"
                myEvent.status shouldBe EventStatus.OPEN.name
            }
        }
    }

    Given("좌석이 아직 등록되지 않은 경기를 보유한 주최자") {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(
            listOf(EventWithSeatCounts(event = buildEvent(2L, EventStatus.SCHEDULED), totalSeats = 0L, soldSeats = 0L)),
            pageable,
            1L,
        )
        every { ticketingDomainService.findEventsWithSeatCountsByOwnerId(ownerId, pageable, null) } returns page

        When("내 경기 목록을 조회하면") {
            val result = listMyEventsUseCase.execute(ownerId, pageable, null)

            // 좌석 0석은 정상 상태다 — null이나 누락이 아니라 0으로 내려가야 화면이 "판매 0 / 0석"을 렌더한다.
            Then("좌석 집계가 누락되지 않고 0으로 내려간다") {
                val myEvent = result.content.first()
                myEvent.totalSeats shouldBe 0L
                myEvent.soldSeats shouldBe 0L
                myEvent.availableSeats shouldBe 0L
            }
        }
    }

    Given("상태 필터를 지정한 주최자") {
        val pageable = PageRequest.of(0, 10)
        val page = PageImpl(
            listOf(EventWithSeatCounts(event = buildEvent(3L, EventStatus.CLOSED), totalSeats = 10L, soldSeats = 10L)),
            pageable,
            1L,
        )
        every {
            ticketingDomainService.findEventsWithSeatCountsByOwnerId(ownerId, pageable, EventStatus.CLOSED)
        } returns page

        When("CLOSED 상태로 조회하면") {
            val result = listMyEventsUseCase.execute(ownerId, pageable, EventStatus.CLOSED)

            Then("매진된 경기의 잔여 좌석은 0이다") {
                result.content.first().availableSeats shouldBe 0L
            }
        }
    }
})
