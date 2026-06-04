package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidTicketStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TicketTest : BehaviorSpec({

    fun buildTicket(status: TicketStatus): Ticket {
        val order = TicketOrder(
            userId = 1L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 10L,
            lockedSeatIds = listOf(101L),
        )
        return Ticket(
            ticketOrder = order,
            seatId = 101L,
            status = status,
            code = "a".repeat(64),
        )
    }

    Given("ISSUED 상태의 Ticket") {
        val ticket = buildTicket(TicketStatus.ISSUED)

        When("revoke()를 호출하면") {
            ticket.revoke()
            Then("[U-02] 상태가 REVOKED로 전이된다") {
                ticket.status shouldBe TicketStatus.REVOKED
            }
        }
    }

    Given("REVOKED 상태의 Ticket") {
        val ticket = buildTicket(TicketStatus.REVOKED)

        When("revoke()를 다시 호출하면") {
            Then("[U-02] InvalidTicketStateException을 던진다") {
                shouldThrow<InvalidTicketStateException> {
                    ticket.revoke()
                }
            }
        }
    }

    Given("Ticket.issue 팩토리 메서드") {
        val order = TicketOrder(
            userId = 5L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 1L,
            lockedSeatIds = listOf(201L),
        )

        When("issue(ticketOrder, seatId)를 호출하면") {
            val ticket = Ticket.issue(ticketOrder = order, seatId = 201L)

            Then("[U-03] code가 64자 엔트로피로 생성된다") {
                ticket.code shouldNotBe null
                ticket.code.length shouldBe 64
            }

            Then("[U-03] 두 번 호출하면 다른 code가 생성된다") {
                val other = Ticket.issue(ticketOrder = order, seatId = 201L)
                ticket.code shouldNotBe other.code
            }

            Then("status가 ISSUED로 초기화된다") {
                ticket.status shouldBe TicketStatus.ISSUED
            }

            Then("ticketOrder와 seatId가 설정된다") {
                ticket.ticketOrder shouldBe order
                ticket.seatId shouldBe 201L
            }
        }
    }

    Given("[U-01] Ticket.issueComplimentary 팩토리 메서드") {
        When("issueComplimentary(seatId)를 호출하면") {
            val ticket = Ticket.issueComplimentary(seatId = 300L)

            Then("[U-01] ticketOrder 가 null 이다") {
                ticket.ticketOrder shouldBe null
            }

            Then("[U-01] seatId 와 status 가 올바르게 설정된다") {
                ticket.seatId shouldBe 300L
                ticket.status shouldBe TicketStatus.ISSUED
            }

            Then("[U-01] isComplimentary 가 true 를 반환한다") {
                ticket.isComplimentary shouldBe true
            }
        }
    }

    Given("[U-02] Ticket.issue 팩토리 메서드 — 정상 주문 발권 경로") {
        val order = TicketOrder(
            userId = 42L,
            status = OrderStatus.PENDING,
            paymentId = null,
            lockedEventId = 2L,
            lockedSeatIds = listOf(400L),
        )

        When("issue(ticketOrder, seatId)를 호출하면") {
            val ticket = Ticket.issue(ticketOrder = order, seatId = 400L)

            Then("[U-02] ticketOrder 가 해당 order를 가리킨다") {
                ticket.ticketOrder shouldBe order
            }

            Then("[U-02] isComplimentary 가 false 를 반환한다") {
                ticket.isComplimentary shouldBe false
            }
        }
    }
})
