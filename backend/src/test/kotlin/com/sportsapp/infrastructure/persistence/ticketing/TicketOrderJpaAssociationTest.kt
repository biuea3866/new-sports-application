package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketOrder
import com.sportsapp.domain.ticketing.TicketStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketOrderJpaAssociationTest(
    @Autowired private val ticketOrderRepositoryImpl: TicketOrderRepositoryImpl,
    @Autowired private val ticketRepositoryImpl: TicketRepositoryImpl,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val eventRepositoryImpl: EventRepositoryImpl,
    @Autowired private val seatRepositoryImpl: SeatRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val startsAt = ZonedDateTime.of(2026, 12, 25, 19, 0, 0, 0, ZoneOffset.UTC)

    init {
        afterEach {
            jdbcTemplate.execute("DELETE FROM tickets")
            jdbcTemplate.execute("DELETE FROM ticket_orders")
            jdbcTemplate.execute("DELETE FROM seats")
            jdbcTemplate.execute("DELETE FROM events")
        }

        Given("TicketOrder를 저장한 뒤 Ticket.issue로 자식 티켓을 생성하고 저장하면") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Association Test Event",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.OPEN,
                    ownerId = 1L,
                )
            )
            val seat = seatRepositoryImpl.saveAll(
                listOf(Seat(id = 0L, eventId = event.id, section = "A", rowNo = "1", seatNo = "1", price = BigDecimal("50000")))
            ).first()

            val order = ticketOrderRepositoryImpl.save(
                TicketOrder.create(userId = 10L, lockedEventId = event.id, lockedSeatIds = listOf(seat.id))
            )
            val ticket = Ticket.issue(ticketOrder = order, seatId = seat.id)
            ticketRepositoryImpl.save(ticket)

            When("findByTicketOrder_Id 와 domain interface로 각각 조회하면") {
                val foundByJpa = ticketJpaRepository.findByTicketOrder_IdAndDeletedAtIsNull(order.id)
                val foundByDomain = ticketRepositoryImpl.findByTicketOrderId(order.id)

                Then("두 조회 모두 ISSUED 상태 티켓이 1건 반환된다") {
                    foundByJpa.size shouldBe 1
                    foundByDomain.size shouldBe 1
                    foundByDomain.first().status shouldBe TicketStatus.ISSUED
                }
            }
        }

        Given("TicketOrder soft-delete 이후 자식 Ticket을 REVOKED로 전환하면") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Orphan Guard Event",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.OPEN,
                    ownerId = 2L,
                )
            )
            val seat = seatRepositoryImpl.saveAll(
                listOf(Seat(id = 0L, eventId = event.id, section = "B", rowNo = "1", seatNo = "1", price = BigDecimal("30000")))
            ).first()

            val order = ticketOrderRepositoryImpl.save(
                TicketOrder.create(userId = 20L, lockedEventId = event.id, lockedSeatIds = listOf(seat.id))
            )
            val ticket = Ticket.issue(ticketOrder = order, seatId = seat.id)
            ticketRepositoryImpl.save(ticket)

            When("티켓을 REVOKED로 전환하고 저장하면") {
                val issuedTickets = ticketRepositoryImpl.findByTicketOrderId(order.id)
                issuedTickets.forEach { it.revoke() }
                ticketRepositoryImpl.saveAll(issuedTickets)

                Then("findByTicketOrderId는 0건, 전체 조회에서는 REVOKED 1건으로 확인된다") {
                    val activeTickets = ticketRepositoryImpl.findByTicketOrderId(order.id)
                    activeTickets.size shouldBe 0

                    val allWithRevoked = ticketJpaRepository.findAll()
                        .filter { it.ticketOrder?.id == order.id }
                    allWithRevoked.size shouldBe 1
                    allWithRevoked.first().status shouldBe TicketStatus.REVOKED
                }
            }
        }

        Given("무료 발급 티켓(ticketOrder = null)을 저장하면") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Complimentary Test",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.OPEN,
                    ownerId = 3L,
                )
            )
            val seat = seatRepositoryImpl.saveAll(
                listOf(Seat(id = 0L, eventId = event.id, section = "C", rowNo = "1", seatNo = "1", price = BigDecimal("0")))
            ).first()

            When("issueComplimentary로 저장하면") {
                val ticket = ticketRepositoryImpl.save(Ticket.issueComplimentary(seatId = seat.id))

                Then("ticket_order_id가 NULL이고 isComplimentary가 true이다") {
                    val found = ticketJpaRepository.findById(ticket.id).get()
                    found.ticketOrder shouldBe null
                    found.isComplimentary shouldBe true
                }
            }
        }
    }
}
