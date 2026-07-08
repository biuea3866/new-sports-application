package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.exception.InvalidOrderStateException
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderJpaRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class TicketOrderCompensationScenarioTest(
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        afterEach {
            val keys = redisTemplate.keys("seat:lock:*")
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
            jdbcTemplate.execute("DELETE FROM tickets")
            jdbcTemplate.execute("DELETE FROM ticket_orders")
        }

        Given("PENDING 상태 TicketOrder에 cancel()을 호출하면") {
            val order = TicketOrder(
                userId = 10L,
                status = OrderStatus.PENDING,
                paymentId = null,
                lockedEventId = 1L,
                lockedSeatIds = listOf(101L),
            )

            When("cancel()을 호출하면") {
                order.cancel()

                Then("상태가 CANCELLED로 전이된다") {
                    order.status shouldBe OrderStatus.CANCELLED
                }
            }
        }

        Given("CONFIRMED 상태 TicketOrder에 cancel()을 재호출하면") {
            val order = TicketOrder(
                userId = 20L,
                status = OrderStatus.CONFIRMED,
                paymentId = 888L,
                lockedEventId = 2L,
                lockedSeatIds = listOf(202L),
            )

            When("cancel()을 호출하면") {
                Then("InvalidOrderStateException이 발생하여 이중 취소가 방지된다") {
                    shouldThrow<InvalidOrderStateException> {
                        order.cancel()
                    }
                }
            }
        }

        Given("CONFIRMED 상태 TicketOrder가 DB에 저장된 상태") {
            val event = eventJpaRepository.save(
                Event(0L, "Confirmed Order Persistence Test", "Busan", baseTime.plusDays(1), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "1", BigDecimal("30000")))

            When("확정 주문의 상태를 조회하면") {
                val saved = ticketOrderJpaRepository.save(
                    TicketOrder(
                        userId = 20L,
                        status = OrderStatus.CONFIRMED,
                        paymentId = 888L,
                        lockedEventId = event.id,
                        lockedSeatIds = listOf(seat.id),
                    )
                )

                val found = ticketOrderJpaRepository.findById(saved.id).orElse(null)

                Then("status가 CONFIRMED이고 paymentId가 non-null로 반환된다") {
                    requireNotNull(found).status shouldBe OrderStatus.CONFIRMED
                    requireNotNull(found).paymentId shouldBe 888L
                }
            }
        }
    }
}
