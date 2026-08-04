package com.sportsapp.application.ticketing.dto

import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.ZonedDateTime

/**
 * 모바일 이벤트 상세 화면(17-이벤트-좌석-선택)의 "구역별 좌석"이 좌석 수만 보여주고
 * 등급별 가격이 없던 결함 검증. 응답의 `seats[].price`는 이미 있었지만 구역 목록에는
 * 대표가가 없어 화면이 어디에도 쓰지 못했다 — `SectionAvailability`에 minPrice/maxPrice를
 * 실어 구역 목록만으로 등급별 가격을 표시할 수 있게 한다.
 */
class EventDetailResponseTest : BehaviorSpec({

    val startsAt = ZonedDateTime.parse("2026-09-01T18:00:00+09:00")

    fun event() = Event(
        id = 1L,
        title = "2026 챔피언십",
        venue = "고척 스카이돔",
        startsAt = startsAt,
        status = EventStatus.OPEN,
        ownerId = 1L,
    )

    fun seat(id: Long, section: String, price: String) = Seat(
        id = id,
        eventId = 1L,
        section = section,
        rowNo = "1",
        seatNo = "$section-0$id",
        price = BigDecimal(price),
    )

    Given("한 구역의 모든 좌석 가격이 동일할 때") {
        val seatsWithAvailability = listOf(
            seat(1L, "R석", "80000") to true,
            seat(2L, "R석", "80000") to false,
        )

        When("응답으로 변환하면") {
            val response = EventDetailResponse.of(event(), seatsWithAvailability)

            Then("구역의 최저가와 최고가가 동일한 단일가로 담긴다") {
                val section = response.sections.single { it.section == "R석" }
                section.totalSeats shouldBe 2
                section.minPrice shouldBe BigDecimal("80000")
                section.maxPrice shouldBe BigDecimal("80000")
            }
        }
    }

    Given("한 구역 안에 가격이 다른 좌석이 섞여 있을 때") {
        val seatsWithAvailability = listOf(
            seat(1L, "S석", "30000") to true,
            seat(2L, "S석", "35000") to true,
            seat(3L, "S석", "50000") to false,
        )

        When("응답으로 변환하면") {
            val response = EventDetailResponse.of(event(), seatsWithAvailability)

            Then("구역의 최저가와 최고가가 각각 담긴다") {
                val section = response.sections.single { it.section == "S석" }
                section.totalSeats shouldBe 3
                section.minPrice shouldBe BigDecimal("30000")
                section.maxPrice shouldBe BigDecimal("50000")
            }
        }
    }

    Given("좌석이 아예 없는 이벤트일 때") {
        val seatsWithAvailability = emptyList<Pair<Seat, Boolean>>()

        When("응답으로 변환하면") {
            val response = EventDetailResponse.of(event(), seatsWithAvailability)

            Then("구역 목록도 좌석 목록도 비어 있다") {
                response.sections shouldBe emptyList()
                response.seats shouldBe emptyList()
            }
        }
    }
})
