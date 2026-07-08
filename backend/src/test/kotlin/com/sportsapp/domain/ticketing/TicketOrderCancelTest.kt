package com.sportsapp.domain.ticketing
import com.sportsapp.domain.ticketing.entity.TicketStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.domain.ticketing.entity.TicketOrder

import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

class BE08TicketOrderCancelTest : BehaviorSpec({

    fun buildOrder(status: OrderStatus) = TicketOrder(
        userId = 1L,
        status = status,
        paymentId = null,
        lockedEventId = 10L,
        lockedSeatIds = listOf(101L),
    )

    Given("[U-03] PENDING 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.PENDING)

        When("cancel()을 호출하면") {
            order.cancel()

            Then("[U-03] 상태가 CANCELLED로 전이된다") {
                order.status shouldBe OrderStatus.CANCELLED
            }
        }
    }

    Given("[U-04] CONFIRMED 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.CONFIRMED)

        When("cancel()을 호출하면") {
            Then("[U-04] InvalidOrderStateException을 던진다") {
                shouldThrow<InvalidOrderStateException> {
                    order.cancel()
                }
            }
        }
    }

    Given("[U-01] ISSUED 상태의 Ticket") {
        val order = TicketOrder(
            userId = 1L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 10L,
            lockedSeatIds = listOf(101L),
        )
        val ticket = Ticket(
            ticketOrder = order,
            seatId = 101L,
            status = TicketStatus.ISSUED,
            code = "a".repeat(64),
        )

        When("revoke()를 호출하면") {
            ticket.revoke()

            Then("[U-01] 상태가 REVOKED로 전이된다") {
                ticket.status shouldBe TicketStatus.REVOKED
            }
        }
    }

    Given("[U-02] REVOKED 상태의 Ticket") {
        val order = TicketOrder(
            userId = 1L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 10L,
            lockedSeatIds = listOf(101L),
        )
        val ticket = Ticket(
            ticketOrder = order,
            seatId = 101L,
            status = TicketStatus.REVOKED,
            code = "b".repeat(64),
        )

        When("revoke()를 재호출하면") {
            Then("[U-02] InvalidTicketStateException을 던진다") {
                shouldThrow<com.sportsapp.domain.ticketing.exception.InvalidTicketStateException> {
                    ticket.revoke()
                }
            }
        }
    }
})
