package com.sportsapp.infrastructure.persistence.ticketing

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class B2bEventRepositoryTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val customSeatRepositoryImpl: CustomSeatRepositoryImpl,
    @Autowired private val eventRepositoryImpl: EventRepositoryImpl,
) : BaseJpaIntegrationTest() {

    private val startsAt = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        Given("[R-01] Event + Seat 100개 저장 후") {
            val event = eventJpaRepository.save(
                Event(0L, "R-01 Event", "Test Venue", startsAt, EventStatus.SCHEDULED, 1L)
            )
            val seats = (1..100).map { index ->
                Seat(
                    id = 0L,
                    eventId = event.id,
                    section = "A",
                    rowNo = ((index - 1) / 10 + 1).toString(),
                    seatNo = ((index - 1) % 10 + 1).toString(),
                    price = BigDecimal("50000"),
                )
            }
            seatJpaRepository.saveAll(seats)

            When("findByEventId로 조회하면") {
                val found = seatJpaRepository.findByEventIdAndDeletedAtIsNull(event.id)

                Then("[R-01] 좌석 수가 정확히 100건이다") {
                    found.size shouldBe 100
                }
            }
        }

        Given("[R-02] Event에 3개의 좌석이 있고 2개에 ISSUED 상태 티켓이 존재할 때") {
            val event = eventJpaRepository.save(
                Event(0L, "R-02 Sales Event", "Sales Venue", startsAt.plusDays(1), EventStatus.OPEN, 2L)
            )
            val seat1 = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "1", BigDecimal("30000")))
            val seat2 = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "2", BigDecimal("30000")))
            val seat3 = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "3", BigDecimal("30000")))

            ticketJpaRepository.save(Ticket(
                ticketOrderId = 1L,
                seatId = seat1.id,
                status = TicketStatus.ISSUED,
                code = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
            ))
            ticketJpaRepository.save(Ticket(
                ticketOrderId = 1L,
                seatId = seat2.id,
                status = TicketStatus.ISSUED,
                code = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
            ))

            When("countSoldByEventId(event.id)를 호출하면") {
                val soldCount = customSeatRepositoryImpl.countSoldByEventId(event.id)

                Then("[R-02] sold count가 2이다") {
                    soldCount shouldBe 2L
                }
            }

            When("REVOKED 티켓이 있어도 sold count에는 포함되지 않는다") {
                ticketJpaRepository.save(Ticket(
                    ticketOrderId = 2L,
                    seatId = seat3.id,
                    status = TicketStatus.REVOKED,
                    code = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                ))
                val soldCount = customSeatRepositoryImpl.countSoldByEventId(event.id)

                Then("[R-02b] REVOKED 티켓은 sold count에 포함되지 않는다") {
                    soldCount shouldBe 2L
                }
            }
        }

        Given("[R-03] ownerId=5인 Event 3건과 다른 ownerId의 Event 2건이 존재할 때") {
            val ownerId = 5L
            eventJpaRepository.save(Event(0L, "Owner5 Event OPEN", "Venue", startsAt.plusDays(2), EventStatus.OPEN, ownerId))
            eventJpaRepository.save(Event(0L, "Owner5 Event SCHEDULED", "Venue", startsAt.plusDays(3), EventStatus.SCHEDULED, ownerId))
            eventJpaRepository.save(Event(0L, "Owner5 Event CLOSED", "Venue", startsAt.plusDays(4), EventStatus.CLOSED, ownerId))
            eventJpaRepository.save(Event(0L, "Other Event 1", "Venue", startsAt.plusDays(5), EventStatus.OPEN, 99L))
            eventJpaRepository.save(Event(0L, "Other Event 2", "Venue", startsAt.plusDays(6), EventStatus.SCHEDULED, 99L))

            When("findByOwnerId(ownerId, pageable) 를 호출하면") {
                val pageable = PageRequest.of(0, 10)
                val result = eventRepositoryImpl.findByOwnerId(ownerId, pageable)

                Then("[R-03] 해당 owner의 이벤트 3건만 반환된다") {
                    result.totalElements shouldBe 3L
                    result.content.all { it.ownerId == ownerId } shouldBe true
                }
            }

            When("findByOwnerId에 status=OPEN 필터를 적용하면") {
                val openEvents = eventJpaRepository.findByOwnerIdAndStatusAndDeletedAtIsNull(
                    ownerId = ownerId,
                    status = EventStatus.OPEN,
                    pageable = PageRequest.of(0, 10),
                )

                Then("[R-03b] OPEN 상태 1건만 반환된다") {
                    openEvents.totalElements shouldBe 1L
                    openEvents.content.first().status shouldBe EventStatus.OPEN
                }
            }
        }
    }
}
