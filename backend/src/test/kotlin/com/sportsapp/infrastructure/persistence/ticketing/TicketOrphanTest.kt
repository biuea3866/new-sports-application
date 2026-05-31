package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketOrder
import com.sportsapp.domain.ticketing.TicketStatus
import com.sportsapp.domain.ticketing.TicketingDomainService
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BE08TicketOrphanTest(
    @Autowired private val ticketingDomainService: TicketingDomainService,
    @Autowired private val ticketRepositoryImpl: TicketRepositoryImpl,
    @Autowired private val seatRepositoryImpl: SeatRepositoryImpl,
    @Autowired private val eventRepositoryImpl: EventRepositoryImpl,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderRepositoryImpl: TicketOrderRepositoryImpl,
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

        Given("[R-01] 주문에 연결된 ISSUED Ticket이 존재하고 cancelOrder를 호출하면") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Test Event",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.OPEN,
                    ownerId = 1L,
                )
            )
            val seat = seatRepositoryImpl.saveAll(
                listOf(
                    Seat(id = 0L, eventId = event.id, section = "A", rowNo = "1", seatNo = "1", price = BigDecimal("50000")),
                )
            ).first()

            val order = ticketOrderRepositoryImpl.save(
                TicketOrder.create(
                    userId = 100L,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(seat.id),
                )
            )
            ticketRepositoryImpl.save(Ticket.issue(ticketOrderId = order.id, seatId = seat.id))

            When("[R-01] cancelOrder를 호출하면") {
                ticketingDomainService.cancelOrder(order.id)

                Then("[R-01] ISSUED Ticket이 REVOKED로 전환되어 findByTicketOrderId가 0건을 반환하고, 전체 조회에서 REVOKED 상태로 확인된다") {
                    val activeTickets = ticketRepositoryImpl.findByTicketOrderId(order.id)
                    activeTickets.size shouldBe 0

                    val allTickets = ticketJpaRepository.findAll()
                        .filter { it.ticketOrderId == order.id }
                    allTickets.size shouldBe 1
                    allTickets.first().status shouldBe TicketStatus.REVOKED
                }
            }
        }

        Given("[R-04] softDeleteByEventId 호출 후 해당 eventId의 Seat 조회 — 0건") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Delete Event Test",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                    ownerId = 2L,
                )
            )
            seatRepositoryImpl.saveAll(
                listOf(
                    Seat(id = 0L, eventId = event.id, section = "B", rowNo = "1", seatNo = "1", price = BigDecimal("30000")),
                    Seat(id = 0L, eventId = event.id, section = "B", rowNo = "1", seatNo = "2", price = BigDecimal("30000")),
                )
            )

            When("[R-04] softDeleteByEventId를 호출하면") {
                seatRepositoryImpl.softDeleteByEventId(event.id, 2L)

                Then("[R-04] findByEventId는 0건을 반환한다") {
                    val seats = seatRepositoryImpl.findByEventId(event.id)
                    seats.size shouldBe 0
                }
            }
        }

        Given("[S-03][S-04] deleteEvent 후 루트 soft-delete → 자식(Seat) 조회 0건") {
            val event = eventRepositoryImpl.save(
                Event(
                    id = 0L,
                    title = "Event To Delete",
                    venue = "Venue",
                    startsAt = startsAt,
                    status = EventStatus.SCHEDULED,
                    ownerId = 3L,
                )
            )
            seatRepositoryImpl.saveAll(
                listOf(
                    Seat(id = 0L, eventId = event.id, section = "C", rowNo = "1", seatNo = "1", price = BigDecimal("40000")),
                    Seat(id = 0L, eventId = event.id, section = "C", rowNo = "1", seatNo = "2", price = BigDecimal("40000")),
                )
            )

            When("[S-03][S-04] deleteEvent를 호출하면") {
                ticketingDomainService.deleteEvent(event.id, 3L)

                Then("[S-03][S-04] seatRepository.findByEventId(eventId)가 0건을 반환하고 전체 Seat의 deletedAt IS NOT NULL이다") {
                    val activeSeats = seatRepositoryImpl.findByEventId(event.id)
                    activeSeats.size shouldBe 0

                    val allSeats = seatJpaRepository.findAll()
                        .filter { it.eventId == event.id }
                    allSeats.size shouldBe 2
                    allSeats.all { it.deletedAt != null } shouldBe true
                }
            }
        }
    }
}
