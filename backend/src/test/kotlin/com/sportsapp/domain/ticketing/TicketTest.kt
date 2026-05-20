package com.sportsapp.domain.ticketing

import com.sportsapp.domain.ticketing.exception.InvalidTicketStateException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TicketTest : BehaviorSpec({

    fun buildTicket(status: TicketStatus) = Ticket(
        ticketOrderId = 1L,
        seatId = 101L,
        status = status,
        code = "a".repeat(64),
    )

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
        When("issue(ticketOrderId, seatId)를 호출하면") {
            val ticket = Ticket.issue(ticketOrderId = 5L, seatId = 201L)

            Then("[U-03] code가 64자 엔트로피로 생성된다") {
                ticket.code shouldNotBe null
                ticket.code.length shouldBe 64
            }

            Then("[U-03] 두 번 호출하면 다른 code가 생성된다") {
                val other = Ticket.issue(ticketOrderId = 5L, seatId = 201L)
                ticket.code shouldNotBe other.code
            }

            Then("status가 ISSUED로 초기화된다") {
                ticket.status shouldBe TicketStatus.ISSUED
            }

            Then("ticketOrderId와 seatId가 설정된다") {
                ticket.ticketOrderId shouldBe 5L
                ticket.seatId shouldBe 201L
            }
        }
    }
})
