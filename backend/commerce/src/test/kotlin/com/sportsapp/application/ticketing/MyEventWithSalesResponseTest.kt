package com.sportsapp.application.ticketing

import com.sportsapp.application.ticketing.dto.MyEventWithSalesResponse
import com.sportsapp.domain.ticketing.dto.EventSalesInfo
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 파트너 포털 경기 상세 화면(`/portal/events/{id}`)이 소비하는 응답 계약 검증.
 *
 * 화면은 판매 현황 수치와 함께 **좌석별 판매 여부 표**를 렌더하므로, 응답에 좌석 목록이
 * 좌석별 판매 플래그와 함께 실려야 한다(좌석 목록이 없으면 화면이 렌더 도중 죽는다).
 */
class MyEventWithSalesResponseTest : BehaviorSpec({

    val startsAt = ZonedDateTime.parse("2026-08-10T19:00:00+09:00")

    fun event() = Event(
        id = 54L,
        title = "2026 시티리그 4강 홈경기",
        venue = "잠실 실내체육관",
        startsAt = startsAt,
        status = EventStatus.OPEN,
        ownerId = 69L,
    )

    fun seat(id: Long, section: String, rowNo: String, seatNo: String) = Seat(
        id = id,
        eventId = 54L,
        section = section,
        rowNo = rowNo,
        seatNo = seatNo,
        price = BigDecimal("44000"),
    )

    Given("좌석 3석 중 1석이 판매된 경기의 판매 정보") {
        val seats = listOf(
            seat(101L, "R석", "1", "R석-01"),
            seat(102L, "R석", "1", "R석-02"),
            seat(103L, "S석", "2", "S석-01"),
        )
        val salesInfo = EventSalesInfo(
            event = event(),
            seats = seats,
            soldCount = 1L,
            soldSeatIds = setOf(102L),
        )

        When("응답으로 변환하면") {
            val response = MyEventWithSalesResponse.of(salesInfo)

            Then("판매 현황 수치가 담긴다") {
                response.id shouldBe 54L
                response.title shouldBe "2026 시티리그 4강 홈경기"
                response.venue shouldBe "잠실 실내체육관"
                response.status shouldBe "OPEN"
                response.totalSeats shouldBe 3
                response.totalSold shouldBe 1L
                response.totalAvailable shouldBe 2L
            }

            Then("좌석 목록이 좌석 수만큼 담긴다") {
                response.seats.size shouldBe 3
            }

            Then("좌석 라벨은 구역·열·번호를 사람이 읽는 형태로 조합한다") {
                response.seats.map { it.label } shouldBe listOf(
                    "R석 1열 R석-01",
                    "R석 1열 R석-02",
                    "S석 2열 S석-01",
                )
            }

            Then("판매된 좌석만 sold=true 로 표시된다") {
                response.seats.single { it.id == 102L }.sold shouldBe true
                response.seats.filter { it.id != 102L }.map { it.sold } shouldBe listOf(false, false)
            }
        }
    }

    Given("좌석이 아직 없는 경기의 판매 정보") {
        val salesInfo = EventSalesInfo(
            event = event(),
            seats = emptyList(),
            soldCount = 0L,
            soldSeatIds = emptySet(),
        )

        When("응답으로 변환하면") {
            val response = MyEventWithSalesResponse.of(salesInfo)

            Then("좌석 목록은 빈 배열이고 수치는 0이다") {
                response.seats shouldBe emptyList()
                response.totalSeats shouldBe 0
                response.totalSold shouldBe 0L
                response.totalAvailable shouldBe 0L
            }
        }
    }
})
