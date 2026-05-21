package com.sportsapp.scenario.ticketing

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Seat
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.SeatJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class EventHostApiScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
    @Autowired private val ticketJpaRepository: TicketJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
    @LocalServerPort private val port: Int,
) : BaseIntegrationTest() {

    private val restTemplate = RestTemplate(
        HttpComponentsClientHttpRequestFactory(HttpClients.createDefault()),
    ).apply {
        errorHandler = object : ResponseErrorHandler {
            override fun hasError(response: ClientHttpResponse): Boolean = false
            override fun handleError(response: ClientHttpResponse) = Unit
        }
    }

    private fun baseUrl() = "http://localhost:$port"

    private fun login(email: String, password: String): String {
        val headers = HttpHeaders().apply { set("Content-Type", "application/json") }
        val body = objectMapper.writeValueAsString(mapOf("email" to email, "password" to password))
        val response = restTemplate.exchange(
            "${baseUrl()}/auth/login",
            HttpMethod.POST,
            HttpEntity(body, headers),
            String::class.java,
        )
        check(response.statusCode == HttpStatus.OK) {
            "Login failed with status ${response.statusCode} for $email"
        }
        return objectMapper.readValue(response.body, LoginResponse::class.java).accessToken
    }

    private fun authHeaders(token: String): HttpHeaders =
        HttpHeaders().apply {
            setBearerAuth(token)
            set("Content-Type", "application/json")
        }

    private fun buildCreateEventBody(seatCount: Int): String {
        val seats = (1..seatCount).map { index ->
            """{"sectionName":"A","seatLabel":"${index}","price":50000}"""
        }.joinToString(",")
        return """
            {
                "title": "Scenario Test Event",
                "venue": "Test Arena",
                "startsAt": "2027-06-01T18:00:00+09:00",
                "seats": [$seats]
            }
        """.trimIndent()
    }

    init {
        val eventHostPassword = "B2bEventHost1!"
        val goodsSellerPassword = "GoodsSeller1!"
        val anotherOwnerPassword = "AnotherOwner1!"

        Given("[S-01] EVENT_HOST 사용자가 Event를 등록하고 일부 좌석 발권 후 판매 현황을 조회할 때") {
            val hostEmail = "event-host-s01@example.com"
            val userId = userDomainService.register(hostEmail, eventHostPassword)
            userDomainService.assignRole(adminId = userId.id, userId = userId.id, roleName = "EVENT_HOST")

            val token = login(hostEmail, eventHostPassword)

            val createBody = buildCreateEventBody(10)
            val createResponse = restTemplate.exchange(
                "${baseUrl()}/api/event-host/events",
                HttpMethod.POST,
                HttpEntity(createBody, authHeaders(token)),
                String::class.java,
            )

            createResponse.statusCode shouldBe HttpStatus.CREATED
            val createJson = objectMapper.readTree(createResponse.body)
            val eventId = createJson.get("eventId").asLong()

            eventId shouldNotBe 0L

            val event = eventJpaRepository.findByIdAndDeletedAtIsNull(eventId)
            requireNotNull(event)
            event.openSales()
            eventJpaRepository.save(event)

            val seats = seatJpaRepository.findByEventIdAndDeletedAtIsNull(eventId)
            seats.size shouldBe 10

            val seatToIssue = seats.first()
            ticketJpaRepository.save(
                Ticket(
                    ticketOrderId = 1L,
                    seatId = seatToIssue.id,
                    status = TicketStatus.ISSUED,
                    code = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", ""),
                )
            )

            When("[S-01] GET /api/event-host/events/{id} 로 판매 현황을 조회하면") {
                val getResponse = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/$eventId",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("sold 수가 1과 일치한다") {
                    getResponse.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(getResponse.body)
                    body.get("totalSold").asInt() shouldBe 1
                    body.get("totalAvailable").asInt() shouldBe 9
                }
            }
        }

        Given("[S-02] EVENT_HOST가 좌석 501개 포함한 Event 등록 요청") {
            val hostEmail = "event-host-s02@example.com"
            val userId = userDomainService.register(hostEmail, eventHostPassword)
            userDomainService.assignRole(adminId = userId.id, userId = userId.id, roleName = "EVENT_HOST")
            val token = login(hostEmail, eventHostPassword)

            When("[S-02] seats.size=501로 POST /api/event-host/events") {
                val body = buildCreateEventBody(501)
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(token)),
                    String::class.java,
                )

                Then("400 Bad Request가 반환된다") {
                    response.statusCode shouldBe HttpStatus.BAD_REQUEST
                }
            }
        }

        Given("[S-03] 좌석 중복 삽입으로 rollback 검증") {
            val hostEmail = "event-host-s03@example.com"
            val userId = userDomainService.register(hostEmail, eventHostPassword)
            userDomainService.assignRole(adminId = userId.id, userId = userId.id, roleName = "EVENT_HOST")
            val token = login(hostEmail, eventHostPassword)

            val totalEventsBefore = eventJpaRepository.count()

            When("[S-03] 동일한 sectionName+seatLabel을 가진 중복 좌석으로 등록 시도") {
                val body = """
                    {
                        "title": "Rollback Test",
                        "venue": "Test Venue",
                        "startsAt": "2027-07-01T18:00:00+09:00",
                        "seats": [
                            {"sectionName":"A","seatLabel":"1","price":10000},
                            {"sectionName":"A","seatLabel":"1","price":20000}
                        ]
                    }
                """.trimIndent()

                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(token)),
                    String::class.java,
                )

                Then("[S-03] 오류 응답이 반환되고 Event 행이 롤백된다") {
                    response.statusCode.is4xxClientError shouldBe true
                    eventJpaRepository.count() shouldBe totalEventsBefore
                }
            }
        }

        Given("[S-04] 다른 owner의 Event에 open/close 시도") {
            val ownerEmail = "event-owner-s04@example.com"
            val ownerId = userDomainService.register(ownerEmail, eventHostPassword)
            userDomainService.assignRole(adminId = ownerId.id, userId = ownerId.id, roleName = "EVENT_HOST")

            val otherOwnerEmail = "event-other-s04@example.com"
            val otherOwnerId = userDomainService.register(otherOwnerEmail, anotherOwnerPassword)
            userDomainService.assignRole(adminId = otherOwnerId.id, userId = otherOwnerId.id, roleName = "EVENT_HOST")

            val ownerEvent = eventJpaRepository.save(
                Event(0L, "S04 Owner Event", "Venue", ZonedDateTime.of(2027, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC), EventStatus.SCHEDULED, ownerId.id)
            )

            val otherToken = login(otherOwnerEmail, anotherOwnerPassword)

            When("[S-04] 다른 owner가 POST /api/event-host/events/{id}/open 호출") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${ownerEvent.id}/open",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(otherToken)),
                    String::class.java,
                )

                Then("404 Not Found가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }

            When("[S-04b] 다른 owner가 POST /api/event-host/events/{id}/close 호출") {
                val openedEvent = eventJpaRepository.findByIdAndDeletedAtIsNull(ownerEvent.id)
                requireNotNull(openedEvent)
                openedEvent.openSales()
                eventJpaRepository.save(openedEvent)

                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${ownerEvent.id}/close",
                    HttpMethod.POST,
                    HttpEntity<Void>(authHeaders(otherToken)),
                    String::class.java,
                )

                Then("404 Not Found가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("[S-05] GOODS_SELLER 사용자가 Event 등록 시도") {
            val sellerEmail = "goods-seller-s05@example.com"
            val userId = userDomainService.register(sellerEmail, goodsSellerPassword)
            userDomainService.assignRole(adminId = userId.id, userId = userId.id, roleName = "GOODS_SELLER")
            val token = login(sellerEmail, goodsSellerPassword)

            When("[S-05] GOODS_SELLER가 POST /api/event-host/events") {
                val body = buildCreateEventBody(1)
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.POST,
                    HttpEntity(body, authHeaders(token)),
                    String::class.java,
                )

                Then("403 Forbidden이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }
    }
}
