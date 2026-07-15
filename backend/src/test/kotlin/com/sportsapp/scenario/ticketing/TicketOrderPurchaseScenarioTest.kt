package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.domain.user.gateway.JwtIssuer
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.TicketOrderJpaRepository
import com.sportsapp.presentation.support.bearerTokenFor
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** AUTH-04 — `X-User-Id` 헤더 대신 `Authorization: Bearer JWT`로 본인 식별한다. */
@AutoConfigureMockMvc
class TicketOrderPurchaseScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketOrderJpaRepository: TicketOrderJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val redisTemplate: StringRedisTemplate,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val jwtIssuer: JwtIssuer,
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
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
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
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
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
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                    header("Idempotency-Key", "purchase-scenario-03")
                    content = requestBody
                }.andReturn()

                Then("403 Forbidden 응답이 반환된다") {
                    result.response.status shouldBe 403
                }
            }
        }

        // 아래 3개 Given은 각각 자기 event/seat/주문을 새로 만드는 독립 블록이다 — 하나의 Given을
        // 공유하고 그 아래에 When을 여러 개 두면, BehaviorSpec은 Given 본문을 딱 한 번만 실행하고
        // 형제 When/Then들이 그 결과(같은 ticketOrderId)를 나눠 쓰는데, 매 leaf 종료마다 도는
        // afterEach가 ticket_orders를 지워버려 두 번째 leaf부터는 이미 삭제된 id를 조회하게 된다
        // (실측: 두 번째 When에서 404). 각 Given을 자기 완결형으로 분리해 이 상호작용을 없앤다.
        Given("PENDING 상태 TicketOrder가 DB에 저장되어 있고 본인이 GET할 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Polling Test Concert", "Seoul", baseTime.plusDays(3), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "D", "1", "1", BigDecimal("20000")))

            val lockId = "${event.id}:${seat.id}"
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", userId.toString())

            val postResult = mockMvc.post("/ticket-orders") {
                contentType = MediaType.APPLICATION_JSON
                header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                header("Idempotency-Key", "purchase-scenario-04")
                content = """{"lockId": "$lockId", "method": "CREDIT_CARD", "currency": "KRW"}"""
            }.andReturn()
            val ticketOrderId = ticketOrderIdOf(postResult)

            When("본인(userId=$userId)이 GET /ticket-orders/{id} 를 호출하면") {
                val getResult = mockMvc.get("/ticket-orders/$ticketOrderId") {
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                }.andReturn()

                Then("200 OK + status=PENDING과 이벤트명·이벤트id·생성일시가 반환된다") {
                    postResult.response.status shouldBe 202
                    getResult.response.status shouldBe 200
                    val body = getResult.response.contentAsString
                    body.contains("PENDING") shouldBe true
                    body.contains(ticketOrderId.toString()) shouldBe true
                    body.contains("Polling Test Concert") shouldBe true
                    body.contains("\"eventId\":${event.id}") shouldBe true
                    body.contains("createdAt") shouldBe true
                }
            }
        }

        Given("PENDING 상태 TicketOrder가 DB에 저장되어 있고 다른 사용자가 GET할 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Polling Test Concert 2", "Seoul", baseTime.plusDays(4), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "E", "1", "1", BigDecimal("20000")))

            val lockId = "${event.id}:${seat.id}"
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", userId.toString())

            val postResult = mockMvc.post("/ticket-orders") {
                contentType = MediaType.APPLICATION_JSON
                header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                header("Idempotency-Key", "purchase-scenario-05")
                content = """{"lockId": "$lockId", "method": "CREDIT_CARD", "currency": "KRW"}"""
            }.andReturn()
            val ticketOrderId = ticketOrderIdOf(postResult)

            When("다른 사용자(userId=99)가 GET /ticket-orders/{id} 를 호출하면") {
                val getResult = mockMvc.get("/ticket-orders/$ticketOrderId") {
                    header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(99L))
                }.andReturn()

                Then("403 Forbidden 응답이 반환된다") {
                    postResult.response.status shouldBe 202
                    getResult.response.status shouldBe 403
                }
            }
        }

        Given("PENDING 상태 TicketOrder가 DB에 저장되어 있고 인증 없이 GET할 때") {
            val event = eventJpaRepository.save(
                Event(0L, "Polling Test Concert 3", "Seoul", baseTime.plusDays(5), EventStatus.OPEN, 1L)
            )
            val seat = seatJpaRepository.save(Seat(0L, event.id, "F", "1", "1", BigDecimal("20000")))

            val lockId = "${event.id}:${seat.id}"
            redisTemplate.opsForValue().set("seat:lock:${event.id}:${seat.id}", userId.toString())

            val postResult = mockMvc.post("/ticket-orders") {
                contentType = MediaType.APPLICATION_JSON
                header(HttpHeaders.AUTHORIZATION, jwtIssuer.bearerTokenFor(userId))
                header("Idempotency-Key", "purchase-scenario-06")
                content = """{"lockId": "$lockId", "method": "CREDIT_CARD", "currency": "KRW"}"""
            }.andReturn()
            val ticketOrderId = ticketOrderIdOf(postResult)

            When("인증 없이 GET /ticket-orders/{id} 를 호출하면") {
                val getResult = mockMvc.get("/ticket-orders/$ticketOrderId").andReturn()

                Then("401 Unauthorized 응답이 반환된다") {
                    postResult.response.status shouldBe 202
                    getResult.response.status shouldBe 401
                }
            }
        }
    }

    private fun ticketOrderIdOf(result: org.springframework.test.web.servlet.MvcResult): Long =
        requireNotNull(
            Regex("\"ticketOrderId\":(\\d+)").find(result.response.contentAsString),
        ) { "POST /ticket-orders 응답에서 ticketOrderId를 찾지 못했습니다: ${result.response.contentAsString}" }
            .groupValues[1]
            .toLong()
}
