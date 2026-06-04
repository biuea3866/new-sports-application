package com.sportsapp.domain.ticketing

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TicketOrderAssociationTest : BehaviorSpec({

    Given("PENDING 상태의 TicketOrder가 있고 2개 좌석 ID로 confirm을 호출하면") {
        val order = TicketOrder.create(
            userId = 1L,
            lockedEventId = 10L,
            lockedSeatIds = listOf(100L, 200L),
        )

        When("confirm(paymentId, seatIds)를 호출하면") {
            val issued = order.confirm(paymentId = 999L, seatIds = listOf(100L, 200L))

            Then("반환된 Ticket 리스트의 각 ticketOrder가 해당 TicketOrder를 가리킨다") {
                issued.size shouldBe 2
                issued.all { it.ticketOrder === order } shouldBe true
            }

            Then("order.tickets 컬렉션에 발행된 티켓들이 포함된다") {
                order.tickets.size shouldBe 2
            }

            Then("order.status가 CONFIRMED로 전이된다") {
                order.status shouldBe OrderStatus.CONFIRMED
            }
        }
    }

    Given("Ticket.issue로 발행한 티켓은 ticketOrder 참조를 가진다") {
        val order = TicketOrder.create(
            userId = 2L,
            lockedEventId = 20L,
            lockedSeatIds = listOf(300L),
        )

        When("Ticket.issue(ticketOrder, seatId)를 호출하면") {
            val ticket = Ticket.issue(ticketOrder = order, seatId = 300L)

            Then("ticket.ticketOrder가 해당 order를 가리킨다") {
                ticket.ticketOrder shouldBe order
            }

            Then("ticket.isComplimentary가 false이다") {
                ticket.isComplimentary shouldBe false
            }
        }
    }

    Given("Ticket.issueComplimentary로 발행한 티켓은 ticketOrder가 null이다") {
        When("issueComplimentary(seatId)를 호출하면") {
            val ticket = Ticket.issueComplimentary(seatId = 400L)

            Then("ticket.ticketOrder가 null이다") {
                ticket.ticketOrder shouldBe null
            }

            Then("ticket.isComplimentary가 true이다") {
                ticket.isComplimentary shouldBe true
            }
        }
    }

    Given("TicketOrder.attachTicket으로 티켓을 수동으로 연결하면") {
        val order = TicketOrder.create(
            userId = 3L,
            lockedEventId = 30L,
            lockedSeatIds = listOf(500L),
        )
        val ticket = Ticket.issue(ticketOrder = order, seatId = 500L)

        When("attachTicket을 호출하면") {
            order.attachTicket(ticket)

            Then("order.tickets에 티켓이 포함된다") {
                order.tickets.size shouldBe 1
                order.tickets.first() shouldBe ticket
            }
        }
    }
})
