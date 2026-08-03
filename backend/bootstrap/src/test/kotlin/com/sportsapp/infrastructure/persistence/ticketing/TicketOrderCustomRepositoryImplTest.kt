package com.sportsapp.infrastructure.persistence.ticketing
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderCustomRepositoryImpl
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderJpaRepository

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.entity.TicketOrder
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketOrderCustomRepositoryImplTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderCustomRepositoryImpl: TicketOrderCustomRepositoryImpl,
) : BaseJpaIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        Given("이벤트가 존재하는 TicketOrder가 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Concert Dec", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            val order = ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 9L,
                    status = OrderStatus.CONFIRMED,
                    paymentId = 100L,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(1L, 2L),
                )
            )

            When("사용자 ID로 주문을 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(9L)

                Then("이벤트명(title)이 포함된 주문이 반환된다") {
                    result.size shouldBe 1
                    result.first().ticketOrderId shouldBe order.id
                    result.first().status shouldBe OrderStatus.CONFIRMED
                    result.first().eventTitle shouldBe "Concert Dec"
                }
            }
        }

        Given("참조 Event가 존재하지 않는(lockedEventId가 부재한) TicketOrder가 있을 때") {
            val order = ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 11L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = 999999L,
                    lockedSeatIds = listOf(3L),
                )
            )

            When("사용자 ID로 주문을 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(11L)

                Then("빈 title로 방어 반환된다") {
                    result.size shouldBe 1
                    result.first().ticketOrderId shouldBe order.id
                    result.first().eventTitle shouldBe ""
                }
            }
        }

        Given("참조 Event가 soft-delete된 TicketOrder가 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Deleted Concert", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            event.softDelete(null)
            eventJpaRepository.save(event)
            val order = ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 12L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(4L),
                )
            )

            When("사용자 ID로 주문을 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(12L)

                Then("삭제된 이벤트의 title 대신 빈 title로 방어 반환된다") {
                    result.size shouldBe 1
                    result.first().ticketOrderId shouldBe order.id
                    result.first().eventTitle shouldBe ""
                }
            }
        }

        Given("다른 사용자의 TicketOrder가 섞여 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Shared Event", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 13L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(5L),
                )
            )
            ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 14L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(6L),
                )
            )

            When("userId=13으로 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(13L)

                Then("userId=13의 주문만 반환된다") {
                    result.size shouldBe 1
                    result.first().eventTitle shouldBe "Shared Event"
                }
            }
        }

        Given("좌석 2석을 예매한 TicketOrder가 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Amount Concert", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            val seat1 = seatJpaRepository.save(Seat(0L, event.id, "R", "1", "R-01", BigDecimal("30000")))
            val seat2 = seatJpaRepository.save(Seat(0L, event.id, "R", "1", "R-02", BigDecimal("30000")))
            val order = ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 15L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(seat1.id, seat2.id),
                )
            )

            When("사용자 ID로 주문을 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(15L)

                Then("좌석가 합계가 totalAmount로 반환된다") {
                    result.size shouldBe 1
                    result.first().ticketOrderId shouldBe order.id
                    result.first().totalAmount shouldBe BigDecimal("60000")
                }

                Then("좌석 요약이 seatSummary로 반환된다") {
                    result.first().seatSummary shouldBe "R 1열 R-01 외 1석"
                }
            }
        }

        Given("좌석 1석만 예매한 TicketOrder가 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Single Seat Concert", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "S", "2", "S-05", BigDecimal("45000")))
            val order = ticketOrderJpaRepository.save(
                TicketOrder(
                    userId = 16L,
                    status = OrderStatus.PENDING,
                    paymentId = null,
                    lockedEventId = event.id,
                    lockedSeatIds = listOf(seat.id),
                )
            )

            When("사용자 ID로 주문을 조회하면") {
                val result = ticketOrderCustomRepositoryImpl.findBy(16L)

                Then("단일 좌석가가 totalAmount로 반환된다") {
                    result.first().totalAmount shouldBe BigDecimal("45000")
                }

                Then("좌석 요약에 '외 N석' 접미사가 붙지 않는다") {
                    result.first().seatSummary shouldBe "S 2열 S-05"
                }
            }
        }
    }
}
