package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.SeatJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketOrderJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
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
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2027, 1, 1, 18, 0, 0, 0, ZoneOffset.UTC)
    private val userId = 42L

    init {
        afterEach {
            val keys = redisTemplate.keys("seat:lock:*")
            if (keys.isNotEmpty()) redisTemplate.delete(keys)
            jdbcTemplate.execute("DELETE FROM ticket_orders")
            jdbcTemplate.execute("DELETE FROM payments")
        }

        Given("[S-01] 좌석 락을 보유한 사용자가 구매 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Purchase Test Concert", "Seoul Arena", baseTime, EventStatus.OPEN)
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

                Then("[S-01] 202 Accepted + ticketOrderId가 반환된다") {
                    result.response.status shouldBe 202
                    val body = result.response.contentAsString
                    body.contains("ticketOrderId") shouldBe true
                }
            }
        }

        Given("[S-02] 락이 만료된 상태에서 구매 요청") {
            val event = eventJpaRepository.save(
                Event(0L, "Expired Lock Concert", "Busan Arena", baseTime.plusDays(1), EventStatus.OPEN)
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

                Then("[S-02] 409 Conflict 응답이 반환된다") {
                    result.response.status shouldBe 409
                }
            }
        }

        Given("[S-03] 다른 사용자의 lockId로 구매 시도") {
            val event = eventJpaRepository.save(
                Event(0L, "Other User Lock Concert", "Incheon", baseTime.plusDays(2), EventStatus.OPEN)
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

                Then("[S-03] 403 Forbidden 응답이 반환된다") {
                    result.response.status shouldBe 403
                }
            }
        }
    }
}
