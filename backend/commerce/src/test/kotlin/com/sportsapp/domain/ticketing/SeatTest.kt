package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.entity.Seat

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

class SeatTest : BehaviorSpec({

    Given("동일한 (eventId, section, rowNo, seatNo) 4튜플을 가진 두 Seat") {
        val seatA = Seat(
            id = 1L,
            eventId = 10L,
            section = "A",
            rowNo = "1",
            seatNo = "5",
            price = BigDecimal("50000"),
        )
        val seatB = Seat(
            id = 99L,
            eventId = 10L,
            section = "A",
            rowNo = "1",
            seatNo = "5",
            price = BigDecimal("99999"),
        )

        When("equals를 호출하면") {
            Then("[U-03] 동등성이 true다") {
                (seatA == seatB) shouldBe true
            }
        }
    }

    Given("다른 seatNo를 가진 두 Seat") {
        val seatA = Seat(
            id = 1L,
            eventId = 10L,
            section = "A",
            rowNo = "1",
            seatNo = "5",
            price = BigDecimal("50000"),
        )
        val seatB = Seat(
            id = 2L,
            eventId = 10L,
            section = "A",
            rowNo = "1",
            seatNo = "6",
            price = BigDecimal("50000"),
        )

        When("equals를 호출하면") {
            Then("[U-03b] 동등성이 false다") {
                (seatA == seatB) shouldBe false
            }
        }
    }

    Given("seatNo에 구역명이 구분자와 함께 접두로 들어있는 좌석") {
        val seat = Seat(
            id = 1L,
            eventId = 10L,
            section = "R석",
            rowNo = "1",
            seatNo = "R석-01",
            price = BigDecimal("50000"),
        )

        When("displayLabel을 조회하면") {
            Then("구역명이 중복되지 않고 한 번만 표기된다") {
                seat.displayLabel shouldBe "R석 01"
            }
        }
    }

    Given("seatNo에 구역명이 구분자 없이 접두로 들어있는 좌석") {
        val seat = Seat(
            id = 1L,
            eventId = 10L,
            section = "R석",
            rowNo = "1",
            seatNo = "R석05",
            price = BigDecimal("50000"),
        )

        When("displayLabel을 조회하면") {
            Then("구역명이 중복되지 않고 한 번만 표기된다") {
                seat.displayLabel shouldBe "R석 05"
            }
        }
    }

    Given("seatNo가 구역명과 무관한 좌석") {
        val seat = Seat(
            id = 1L,
            eventId = 10L,
            section = "S석",
            rowNo = "1",
            seatNo = "B12",
            price = BigDecimal("50000"),
        )

        When("displayLabel을 조회하면") {
            Then("seatNo가 그대로 유지된다") {
                seat.displayLabel shouldBe "S석 B12"
            }
        }
    }

    Given("rowNo가 미수집 센티널 값(\"1\")인 좌석") {
        val seat = Seat(
            id = 1L,
            eventId = 10L,
            section = "A석",
            rowNo = "1",
            seatNo = "07",
            price = BigDecimal("50000"),
        )

        When("displayLabel을 조회하면") {
            Then("수집하지 않은 열 정보를 사실처럼 표기하지 않도록 열 파트가 생략된다") {
                seat.displayLabel shouldBe "A석 07"
            }
        }
    }

    Given("rowNo가 미수집 센티널이 아닌 실제 값(\"2\")인 좌석") {
        val seat = Seat(
            id = 1L,
            eventId = 10L,
            section = "S석",
            rowNo = "2",
            seatNo = "S석-01",
            price = BigDecimal("44000"),
        )

        When("displayLabel을 조회하면") {
            Then("열 정보가 그대로 표기된다") {
                seat.displayLabel shouldBe "S석 2열 01"
            }
        }
    }
})
