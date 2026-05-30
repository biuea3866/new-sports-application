package com.sportsapp.scenario.ticketing

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.user.LoginResponse
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.ticketing.Ticket
import com.sportsapp.domain.ticketing.TicketStatus
import com.sportsapp.domain.user.UserDomainService
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import com.sportsapp.infrastructure.persistence.ticketing.TicketJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.data.repository.findByIdOrNull
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.client.ClientHttpResponse
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.ResponseErrorHandler
import org.springframework.web.client.RestTemplate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.UUID

class EventDeleteScenarioTest(
    @Autowired private val userDomainService: UserDomainService,
    @Autowired private val eventJpaRepository: EventJpaRepository,
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

    private fun createScheduledEvent(ownerId: Long): Event =
        eventJpaRepository.save(
            Event(
                id = 0L,
                title = "Delete Scenario Event",
                venue = "Test Arena",
                startsAt = ZonedDateTime.of(2027, 9, 1, 18, 0, 0, 0, ZoneOffset.UTC),
                status = EventStatus.SCHEDULED,
                ownerId = ownerId,
            )
        )

    init {
        val hostPassword = "EventHost1!"
        val anotherPassword = "AnotherHost1!"

        Given("[S-06] host가 미오픈 경기를 삭제하면 deletedAt이 채워지고 목록 조회에서 제외된다") {
            val hostEmail = "delete-host-s06@example.com"
            val host = userDomainService.register(hostEmail, hostPassword)
            userDomainService.assignRole(adminId = host.id, userId = host.id, roleName = "EVENT_HOST")
            val token = login(hostEmail, hostPassword)

            val event = createScheduledEvent(host.id)

            When("[S-06] DELETE /api/event-host/events/{id} 호출") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${event.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("204 No Content가 반환되고 deletedAt이 채워진다") {
                    response.statusCode shouldBe HttpStatus.NO_CONTENT
                    val deleted = eventJpaRepository.findByIdOrNull(event.id)
                    deleted shouldNotBe null
                    deleted?.deletedAt shouldNotBe null
                }
            }

            When("[S-06b] 삭제 후 GET /api/event-host/events 목록 조회") {
                val listResponse = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events",
                    HttpMethod.GET,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("삭제된 경기가 목록에 포함되지 않는다") {
                    listResponse.statusCode shouldBe HttpStatus.OK
                    val body = objectMapper.readTree(listResponse.body)
                    val content = body.get("content")
                    val ids = (0 until content.size()).map { content.get(it).get("id").asLong() }
                    ids.contains(event.id) shouldBe false
                }
            }
        }

        Given("[S-07] 티켓이 발행된 경기를 삭제하면 4xx가 반환된다") {
            val hostEmail = "delete-host-s07@example.com"
            val host = userDomainService.register(hostEmail, hostPassword)
            userDomainService.assignRole(adminId = host.id, userId = host.id, roleName = "EVENT_HOST")
            val token = login(hostEmail, hostPassword)

            val event = eventJpaRepository.save(
                Event(
                    id = 0L,
                    title = "Open Event S07",
                    venue = "Stadium",
                    startsAt = ZonedDateTime.of(2027, 9, 2, 18, 0, 0, 0, ZoneOffset.UTC),
                    status = EventStatus.OPEN,
                    ownerId = host.id,
                )
            )

            ticketJpaRepository.save(
                Ticket(
                    ticketOrderId = 999L,
                    seatId = 1L,
                    status = TicketStatus.ISSUED,
                    code = UUID.randomUUID().toString().replace("-", "") +
                        UUID.randomUUID().toString().replace("-", ""),
                )
            )

            When("[S-07] DELETE /api/event-host/events/{id} 호출") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${event.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(authHeaders(token)),
                    String::class.java,
                )

                Then("422 Unprocessable Entity가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
                }
            }
        }

        Given("[S-08] 본인이 아닌 사용자가 경기 삭제 요청 시 404가 반환된다") {
            val ownerEmail = "delete-owner-s08@example.com"
            val otherEmail = "delete-other-s08@example.com"
            val owner = userDomainService.register(ownerEmail, hostPassword)
            val other = userDomainService.register(otherEmail, anotherPassword)
            userDomainService.assignRole(adminId = owner.id, userId = owner.id, roleName = "EVENT_HOST")
            userDomainService.assignRole(adminId = other.id, userId = other.id, roleName = "EVENT_HOST")
            val otherToken = login(otherEmail, anotherPassword)

            val event = createScheduledEvent(owner.id)

            When("[S-08] 다른 host가 DELETE /api/event-host/events/{id} 호출") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${event.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(authHeaders(otherToken)),
                    String::class.java,
                )

                Then("404 Not Found가 반환된다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }

        Given("[S-09] 인증 없이 삭제 요청 시 401이 반환된다") {
            val hostEmail = "delete-noauth-s09@example.com"
            val host = userDomainService.register(hostEmail, hostPassword)
            userDomainService.assignRole(adminId = host.id, userId = host.id, roleName = "EVENT_HOST")

            val event = createScheduledEvent(host.id)

            When("[S-09] 인증 헤더 없이 DELETE /api/event-host/events/{id} 호출") {
                val response = restTemplate.exchange(
                    "${baseUrl()}/api/event-host/events/${event.id}",
                    HttpMethod.DELETE,
                    HttpEntity<Void>(HttpHeaders()),
                    String::class.java,
                )

                Then("401 Unauthorized가 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNAUTHORIZED
                }
            }
        }
    }
}
