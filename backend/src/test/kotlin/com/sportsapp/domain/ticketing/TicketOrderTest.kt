package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TicketOrderTest : BehaviorSpec({

    fun buildOrder(status: OrderStatus) = TicketOrder(
        userId = 1L,
        status = status,
        paymentId = null,
        lockedEventId = 10L,
        lockedSeatIds = listOf(101L, 102L),
    )

    Given("PENDING 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.PENDING)

        When("confirm(paymentId, seatIds)를 호출하면") {
            val tickets = order.confirm(paymentId = 999L, seatIds = listOf(101L, 102L))

            Then("[U-01] 상태가 CONFIRMED로 전이된다") {
                order.status shouldBe OrderStatus.CONFIRMED
            }

            Then("[U-01] paymentId가 설정된다") {
                order.paymentId shouldBe 999L
            }

            Then("[U-01] seatIds 수만큼 Ticket이 반환된다") {
                tickets shouldHaveSize 2
                tickets[0].seatId shouldBe 101L
                tickets[1].seatId shouldBe 102L
            }
        }
    }

    Given("CONFIRMED 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.CONFIRMED)

        When("confirm()을 다시 호출하면") {
            Then("[U-01] InvalidOrderStateException을 던진다") {
                shouldThrow<InvalidOrderStateException> {
                    order.confirm(paymentId = 999L, seatIds = listOf(101L))
                }
            }
        }
    }

    Given("CANCELLED 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.CANCELLED)

        When("confirm()을 호출하면") {
            Then("[U-01] InvalidOrderStateException을 던진다") {
                shouldThrow<InvalidOrderStateException> {
                    order.confirm(paymentId = 999L, seatIds = listOf(101L))
                }
            }
        }
    }

    Given("PENDING 상태의 TicketOrder") {
        val order = buildOrder(OrderStatus.PENDING)

        When("cancel()을 호출하면") {
            order.cancel()
            Then("상태가 CANCELLED로 전이된다") {
                order.status shouldBe OrderStatus.CANCELLED
            }
        }
    }

    Given("소유자 userId=1의 TicketOrder") {
        val order = buildOrder(OrderStatus.PENDING)

        When("requireOwnedBy(1)을 호출하면") {
            Then("예외 없이 통과한다") {
                order.requireOwnedBy(1L)
            }
        }

        When("requireOwnedBy(2)를 호출하면") {
            Then("BusinessRuleViolationException을 던진다") {
                shouldThrow<com.sportsapp.domain.common.exceptions.BusinessRuleViolationException> {
                    order.requireOwnedBy(2L)
                }
            }
        }
    }

    Given("PENDING 상태의 TicketOrder에 confirm 후 반환된 Ticket") {
        val order = buildOrder(OrderStatus.PENDING)
        val tickets = order.confirm(paymentId = 1L, seatIds = listOf(201L, 202L))

        Then("[U-03] 각 Ticket code가 64자 unique 식별자로 생성된다") {
            tickets.forEach { ticket ->
                ticket.code shouldNotBe null
                ticket.code.length shouldBe 64
            }
            tickets[0].code shouldNotBe tickets[1].code
        }
    }
})
