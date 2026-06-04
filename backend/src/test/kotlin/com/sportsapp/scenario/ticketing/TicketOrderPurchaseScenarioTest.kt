package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class TicketOrderPurchaseScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2027, 1, 1, 18, 0, 0, 0, ZoneOffset.UTC)
    private val userId = 42L

    init {
        afterEach {
            val keys = redisTemplate.keys("seat:lock:*")
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
            jdbcTemplate.execute("DELETE FROM tickets")
            jdbcTemplate.execute("DELETE FROM ticket_orders")
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("좌석 락을 보유한 사용자가 구매 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Purchase Test Concert", "Seoul Arena", baseTime, EventStatus.OPEN, 1L)
            )
            val seat1 = seatJpaRepository.save(Seat(0L, event.id, "A", "1", "1", BigDecimal("30000")))
            val seat2 = seatJpaRepository.save(Seat(0L, event.id, "A", "1", "2", BigDecimal("30000")))

            val lockId = "${event.id}:${seat1.id},${event.id}:${seat2.id}"
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat1.id}", userId.toString())
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat2.id}", userId.toString())

            When("POST /ticket-orders 를 호출하면") {
                val requestBody = """
                    {
                        "lockId": "$lockId",
                        "method": "CREDIT_CARD",
                        "currency": "KRW"
                    }
                """.trimIndent()

                val result = mockMvc.post("/ticket-orders") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", userId.toString())
                    header("Idempotency-Key", "purchase-scenario-01")
                    content = requestBody
                }.andReturn()

                Then("202 Accepted + ticketOrderId가 반환되고 DB status가 PENDING이며 Ticket이 발급되지 않는다") {
                    result.response.status shouldBe 202
                    val body = result.response.contentAsString
                    body.contains("ticketOrderId") shouldBe true
                    body.contains("PENDING") shouldBe true

                    val orders = ticketOrderJpaRepository.findAll()
                    orders.size shouldBe 1
                    orders.first().status shouldBe OrderStatus.PENDING

                    val tickets = ticketJpaRepository.findAll()
                    tickets.size shouldBe 0
                }
            }
        }

        Given("락이 만료된 상태에서 구매 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Expired Lock Concert", "Busan Arena", baseTime.plusDays(1), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "B", "1", "1", BigDecimal("50000")))

            val lockId = "${event.id}:${seat.id}"

            When("Redis에 락이 없는 상태로 POST /ticket-orders 호출") {
                val requestBody = """
                    {
                        "lockId": "$lockId",
                        "method": "CREDIT_CARD",
                        "currency": "KRW"
                    }
                """.trimIndent()

                val result = mockMvc.post("/ticket-orders") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", userId.toString())
                    header("Idempotency-Key", "purchase-scenario-02")
                    content = requestBody
                }.andReturn()

                Then("409 Conflict 응답이 반환된다") {
                    result.response.status shouldBe 409
                }
            }
        }

        Given("다른 사용자의 lockId로 구매 시도") {
            val event = eventJpaRepository.save(
                Event(0L, "Other User Lock Concert", "Incheon", baseTime.plusDays(2), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "C", "1", "1", BigDecimal("40000")))

            val lockId = "${event.id}:${seat.id}"
            val otherUserId = 99L
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", otherUserId.toString())

            When("userId=$userId 가 userId=$otherUserId 소유 락으로 POST /ticket-orders 호출") {
                val requestBody = """
                    {
                        "lockId": "$lockId",
                        "method": "CREDIT_CARD",
                        "currency": "KRW"
                    }
                """.trimIndent()

                val result = mockMvc.post("/ticket-orders") {
                    contentType = MediaType.APPLICATION_JSON
                    header("X-User-Id", userId.toString())
                    header("Idempotency-Key", "purchase-scenario-03")
                    content = requestBody
                }.andReturn()

                Then("403 Forbidden 응답이 반환된다") {
                    result.response.status shouldBe 403
                }
            }
        }

        Given("PENDING 상태 TicketOrder가 DB에 저장되어 있을 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Polling Test Concert", "Seoul", baseTime.plusDays(3), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "D", "1", "1", BigDecimal("20000")))

            val lockId = "${event.id}:${seat.id}"
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", userId.toString())

            val postResult = mockMvc.post("/ticket-orders") {
                contentType = MediaType.APPLICATION_JSON
                header("X-User-Id", userId.toString())
                header("Idempotency-Key", "purchase-scenario-04")
                content = """{"lockId": "$lockId", "method": "CREDIT_CARD", "currency": "KRW"}"""
            }.andReturn()

            val ticketOrderId = ticketOrderJpaRepository.findAll().first().id

            When("GET /ticket-orders/{id} 를 호출하면") {
                val getResult = mockMvc.get("/ticket-orders/$ticketOrderId").andReturn()

                Then("200 OK + status=PENDING이 반환된다") {
                    postResult.response.status shouldBe 202
                    getResult.response.status shouldBe 200
                    getResult.response.contentAsString.contains("PENDING") shouldBe true
                    getResult.response.contentAsString.contains(ticketOrderId.toString()) shouldBe true
                }
            }
        }
    }
}
