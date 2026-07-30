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
})
