package com.sportsapp.infrastructure.persistence.ticketing
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderCustomRepositoryImpl
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderJpaRepository

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketOrderCustomRepositoryImplTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
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
    }
}
