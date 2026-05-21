package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.ticketing.PurchaseTicketsCommand
import com.sportsapp.application.ticketing.PurchaseTicketsUseCase
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.OrderStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.payment.PaymentMethod
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.SeatJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketOrderJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.TestPropertySource
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

@TestPropertySource(properties = ["app.payment.mock.success-rate=0.0"])
class TicketOrderCompensationScenarioTest(
    @Autowired private val purchaseTicketsUseCase: PurchaseTicketsUseCase,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2027, 6, 1, 18, 0, 0, 0, ZoneOffset.UTC)
    private val userId = 77L

    init {
        afterEach {
            val keys = redisTemplate.keys("seat:lock:*")
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
            jdbcTemplate.execute("DELETE FROM ticket_orders")
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("[S-comp-01] 좌석 락을 보유한 사용자가 구매 요청 — PG 항상 실패 환경") {
            var lockId = ""

            beforeTest {
                val event = eventJpaRepository.save(
                    Event(0L, "Compensation Test Concert", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
                )
                val seat = seatJpaRepository.save(Seat(0L, event.id, "A", "1", "1", BigDecimal("50000")))
                lockId = "${event.id}:${seat.id}"
                redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", userId.toString())
            }

            When("purchaseTicketsUseCase.execute를 호출하면") {
                val command = PurchaseTicketsCommand(
                    userId = userId,
                    lockId = lockId,
                    idempotencyKey = "comp-test-idem-01",
                    method = PaymentMethod.CREDIT_CARD,
                    currency = "KRW",
                )

                purchaseTicketsUseCase.execute(command)

                Then("[S-comp-01] PG 5xx 실패 시 TicketOrder가 CANCELLED로 전이된다") {
                    val orders = ticketOrderJpaRepository.findAll()
                    orders.size shouldBe 1
                    orders.first().status shouldBe OrderStatus.CANCELLED
                }
            }
        }
    }
}
