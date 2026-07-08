package com.sportsapp.infrastructure.persistence.kpi

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.entity.Ticket
import com.sportsapp.infrastructure.booking.mysql.BookingJpaRepository
import com.sportsapp.infrastructure.booking.mysql.BookingKpiQueryRepositoryImpl
import com.sportsapp.infrastructure.booking.mysql.SlotJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderCustomRepositoryImpl
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class OperationKpiQueryTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val bookingKpiQueryRepositoryImpl: BookingKpiQueryRepositoryImpl,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val ticketOrderCustomRepositoryImpl: TicketOrderCustomRepositoryImpl,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private val ownerUserId = 777L
    private val from = ZonedDateTime.now().minusDays(30)
    private val to = ZonedDateTime.now().plusDays(1)

    init {
        Given("[R-01] ownerUserId=777 슬롯 2개, CONFIRMED 예약 2건, CANCELLED 예약 1건이 있을 때") {
            jdbcTemplate.execute(
                "DELETE FROM bookings WHERE slot_id IN (SELECT id FROM slots WHERE owner_id = $ownerUserId)"
            )
            jdbcTemplate.execute("DELETE FROM slots WHERE owner_id = $ownerUserId")

            val slot1 = slotJpaRepository.save(
                Slot.create("fac-kpi-1", ZonedDateTime.now(), "10:00-12:00", 10, ownerUserId)
            )
            val slot2 = slotJpaRepository.save(
                Slot.create("fac-kpi-1", ZonedDateTime.now(), "14:00-16:00", 10, ownerUserId)
            )

            bookingJpaRepository.save(
                Booking(userId = 1L, slotId = slot1.id, initialStatus = BookingStatus.CONFIRMED, initialPaymentId = null)
            )
            bookingJpaRepository.save(
                Booking(userId = 2L, slotId = slot2.id, initialStatus = BookingStatus.CONFIRMED, initialPaymentId = null)
            )
            bookingJpaRepository.save(
                Booking(userId = 3L, slotId = slot1.id, initialStatus = BookingStatus.CANCELLED, initialPaymentId = null)
            )

            When("ownerUserId=777의 CONFIRMED 예약 카운트를 조회하면") {
                val confirmedCount = bookingKpiQueryRepositoryImpl
                    .countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)

                Then("[R-01] CONFIRMED 예약 2건이 집계된다") {
                    confirmedCount shouldBe 2L
                }
            }

            When("ownerUserId=777의 총 슬롯 용량을 조회하면") {
                val totalCapacity = bookingKpiQueryRepositoryImpl
                    .sumSlotCapacityByOwnerUserIdAndDateRange(ownerUserId, from, to)

                Then("[R-01] 슬롯 2개 합산 용량 20이 반환된다") {
                    totalCapacity shouldBe 20L
                }
            }
        }

        Given("[R-02] ownerUserId=777 슬롯에 REFUNDED 예약 1건이 있을 때") {
            jdbcTemplate.execute(
                "DELETE FROM bookings WHERE slot_id IN (SELECT id FROM slots WHERE owner_id = $ownerUserId)"
            )
            jdbcTemplate.execute("DELETE FROM slots WHERE owner_id = $ownerUserId")

            val slot = slotJpaRepository.save(
                Slot.create("fac-kpi-2", ZonedDateTime.now(), "09:00-11:00", 5, ownerUserId)
            )
            bookingJpaRepository.save(
                Booking(userId = 10L, slotId = slot.id, initialStatus = BookingStatus.REFUNDED, initialPaymentId = null)
            )

            When("ownerUserId=777의 노쇼(REFUNDED) 예약 카운트를 조회하면") {
                val noShowCount = bookingKpiQueryRepositoryImpl
                    .countRefundedByOwnerUserIdAndDateRange(ownerUserId, from, to)

                Then("[R-02] REFUNDED 예약 1건이 노쇼로 집계된다") {
                    noShowCount shouldBe 1L
                }
            }
        }

        Given("[R-03] ownerUserId=777 이벤트에 Complimentary 티켓 2건이 있을 때") {
            cleanupTicketData()

            val event = eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "KPI 테스트 이벤트",
                    venue = "서울",
                    startsAt = ZonedDateTime.now().plusDays(5),
                    status = EventStatus.OPEN,
                    ownerId = ownerUserId,
                )
            )
            val seat1 = seatJpaRepository.save(
                Seat(id = 0L, eventId = event.id, section = "A", rowNo = "1", seatNo = "1", price = BigDecimal("10000"))
            )
            val seat2 = seatJpaRepository.save(
                Seat(id = 0L, eventId = event.id, section = "A", rowNo = "1", seatNo = "2", price = BigDecimal("10000"))
            )

            ticketJpaRepository.save(Ticket.issueComplimentary(seat1.id))
            ticketJpaRepository.save(Ticket.issueComplimentary(seat2.id))

            When("ownerUserId=777의 Complimentary 티켓 카운트를 조회하면") {
                val complimentaryCount = ticketOrderCustomRepositoryImpl
                    .countComplimentaryByOwnerUserIdAndDateRange(ownerUserId, from, to)

                Then("[R-03] Complimentary 티켓 2건이 집계된다") {
                    complimentaryCount shouldBe 2L
                }
            }
        }

        Given("[R-04] 다른 ownerUserId=999의 데이터가 혼재할 때") {
            val otherOwnerUserId = 999L
            jdbcTemplate.execute(
                "DELETE FROM bookings WHERE slot_id IN (SELECT id FROM slots WHERE owner_id = $ownerUserId)"
            )
            jdbcTemplate.execute("DELETE FROM slots WHERE owner_id = $ownerUserId")
            jdbcTemplate.execute(
                "DELETE FROM bookings WHERE slot_id IN (SELECT id FROM slots WHERE owner_id = $otherOwnerUserId)"
            )
            jdbcTemplate.execute("DELETE FROM slots WHERE owner_id = $otherOwnerUserId")

            val mySlot = slotJpaRepository.save(
                Slot.create("fac-kpi-own", ZonedDateTime.now(), "10:00-12:00", 5, ownerUserId)
            )
            val otherSlot = slotJpaRepository.save(
                Slot.create("fac-kpi-other", ZonedDateTime.now(), "10:00-12:00", 5, otherOwnerUserId)
            )

            bookingJpaRepository.save(
                Booking(userId = 1L, slotId = mySlot.id, initialStatus = BookingStatus.CONFIRMED, initialPaymentId = null)
            )
            bookingJpaRepository.save(
                Booking(userId = 2L, slotId = otherSlot.id, initialStatus = BookingStatus.CONFIRMED, initialPaymentId = null)
            )

            When("ownerUserId=777로만 필터링하면") {
                val confirmedCount = bookingKpiQueryRepositoryImpl
                    .countConfirmedByOwnerUserIdAndDateRange(ownerUserId, from, to)

                Then("[R-04] IDOR 차단 — ownerUserId=777의 예약 1건만 반환된다") {
                    confirmedCount shouldBe 1L
                }
            }
        }
    }

    private fun cleanupTicketData() {
        jdbcTemplate.execute(
            "DELETE FROM tickets WHERE seat_id IN " +
                "(SELECT id FROM seats WHERE event_id IN (SELECT id FROM events WHERE owner_id = $ownerUserId))"
        )
        jdbcTemplate.execute(
            "DELETE FROM ticket_orders WHERE locked_event_id IN " +
                "(SELECT id FROM events WHERE owner_id = $ownerUserId)"
        )
        jdbcTemplate.execute(
            "DELETE FROM seats WHERE event_id IN (SELECT id FROM events WHERE owner_id = $ownerUserId)"
        )
        jdbcTemplate.execute("DELETE FROM events WHERE owner_id = $ownerUserId")
    }
}
