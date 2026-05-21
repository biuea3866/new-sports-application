package com.sportsapp.scenario.ticketing

import com.fasterxml.jackson.databind.ObjectMapper
import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.ticketing.Event
import com.sportsapp.domain.ticketing.EventStatus
import com.sportsapp.domain.user.UserPrincipal
import com.sportsapp.infrastructure.persistence.ticketing.EventJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.test.web.servlet.put
import java.time.ZoneOffset
import java.time.ZonedDateTime

@AutoConfigureMockMvc
class B2bEventScenarioTest(
    @Autowired private val mockMvc: MockMvc,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val objectMapper: ObjectMapper,
) : BaseJpaIntegrationTest() {

    private val ownerUserId = 101L
    private val principal = UserPrincipal(id = ownerUserId, email = "host@test.com", roles = listOf("EVENT_HOST"))
    private val auth = UsernamePasswordAuthenticationToken(
        principal, null, listOf(SimpleGrantedAuthority("ROLE_EVENT_HOST"))
    )

    init {
        Given("[S-01] EVENT_HOST 권한 사용자가 이벤트 생성 요청") {
            val requestBody = mapOf(
                "title" to "Summer Festival",
                "venue" to "Seoul Arena",
                "startsAt" to "2027-07-01T18:00:00+09:00",
                "seats" to listOf(
                    mapOf("section" to "A", "rowNo" to "1", "seatNo" to "1", "price" to 50000)
                ),
            )

            When("POST /b2b/my/events 를 호출하면") {
                val result = mockMvc.post("/b2b/my/events") {
                    contentType = MediaType.APPLICATION_JSON
                    with(authentication(auth))
                    content = objectMapper.writeValueAsString(requestBody)
                }.andReturn()

                Then("[S-01] 201 Created + SCHEDULED 이벤트가 반환되고 DB에 저장된다") {
                    result.response.status shouldBe 201
                    val body = result.response.contentAsString
                    body.contains("SCHEDULED") shouldBe true
                    body.contains("Summer Festival") shouldBe true

                    val saved = eventJpaRepository.findAll()
                        .filter { it.title == "Summer Festival" && it.ownerId == ownerUserId }
                    saved.size shouldBe 1
                    saved.first().status shouldBe EventStatus.SCHEDULED
                }
            }
        }

        Given("[S-02] ownerUserId=101 의 이벤트 2건이 저장된 상태") {
            val baseTime = ZonedDateTime.of(2027, 8, 1, 18, 0, 0, 0, ZoneOffset.UTC)
            eventJpaRepository.save(Event(0L, "Event One", "Seoul", baseTime, EventStatus.SCHEDULED, ownerUserId))
            eventJpaRepository.save(Event(0L, "Event Two", "Busan", baseTime.plusDays(1), EventStatus.OPEN, ownerUserId))

            When("GET /b2b/my/events 를 호출하면") {
                val result = mockMvc.get("/b2b/my/events") {
                    with(authentication(auth))
                }.andReturn()

                Then("[S-02] 200 OK + confirmedSeatCount가 포함된 목록이 반환된다") {
                    result.response.status shouldBe 200
                    val body = result.response.contentAsString
                    body.contains("confirmedSeatCount") shouldBe true
                }
            }
        }

        Given("[S-03] SCHEDULED 이벤트가 저장된 상태") {
            val event = eventJpaRepository.save(
                Event(0L, "Update Target", "Seoul", ZonedDateTime.now().plusDays(30), EventStatus.SCHEDULED, ownerUserId)
            )

            When("PUT /b2b/my/events/{id} 를 호출하면") {
                val requestBody = mapOf(
                    "title" to "Updated Title",
                    "venue" to "Busan Arena",
                    "startsAt" to "2027-10-01T18:00:00+09:00",
                )
                val result = mockMvc.put("/b2b/my/events/${event.id}") {
                    contentType = MediaType.APPLICATION_JSON
                    with(authentication(auth))
                    content = objectMapper.writeValueAsString(requestBody)
                }.andReturn()

                Then("[S-03] 200 OK + 갱신된 title/venue가 반환된다") {
                    result.response.status shouldBe 200
                    val body = result.response.contentAsString
                    body.contains("Updated Title") shouldBe true
                    body.contains("Busan Arena") shouldBe true
                }
            }
        }

        Given("[S-04] OPEN 이벤트가 저장된 상태") {
            val event = eventJpaRepository.save(
                Event(0L, "Close Target", "Seoul", ZonedDateTime.now().plusDays(30), EventStatus.OPEN, ownerUserId)
            )

            When("POST /b2b/my/events/{id}/close 를 호출하면") {
                val result = mockMvc.post("/b2b/my/events/${event.id}/close") {
                    with(authentication(auth))
                }.andReturn()

                Then("[S-04] 200 OK + CLOSED 상태가 반환된다") {
                    result.response.status shouldBe 200
                    val body = result.response.contentAsString
                    body.contains("CLOSED") shouldBe true

                    val closed = eventJpaRepository.findById(event.id).get()
                    closed.status shouldBe EventStatus.CLOSED
                }
            }
        }

        Given("[S-06] 미인증 사용자가 B2B API에 접근할 때") {
            When("GET /b2b/my/events 를 인증 없이 호출하면") {
                val result = mockMvc.get("/b2b/my/events").andReturn()

                Then("[S-06] 401 Unauthorized가 반환된다") {
                    result.response.status shouldBe 401
                }
            }
        }

        Given("[S-07] EVENT_HOST 권한이 없는 USER 역할 사용자가 B2B API에 접근할 때") {
            val userOnlyPrincipal = UserPrincipal(id = 200L, email = "user@test.com", roles = listOf("USER"))
            val userOnlyAuth = UsernamePasswordAuthenticationToken(
                userOnlyPrincipal, null, listOf(SimpleGrantedAuthority("ROLE_USER"))
            )

            When("GET /b2b/my/events 를 호출하면") {
                val result = mockMvc.get("/b2b/my/events") {
                    with(authentication(userOnlyAuth))
                }.andReturn()

                Then("[S-07] 403 Forbidden이 반환된다") {
                    result.response.status shouldBe 403
                }
            }
        }

        Given("[S-05] 다른 owner의 이벤트에 접근할 때") {
            val otherOwnerId = 999L
            val event = eventJpaRepository.save(
                Event(0L, "Other Owner Event", "Seoul", ZonedDateTime.now().plusDays(30), EventStatus.SCHEDULED, otherOwnerId)
            )

            When("GET /b2b/my/events/{id} 를 호출하면") {
                val result = mockMvc.get("/b2b/my/events/${event.id}") {
                    with(authentication(auth))
                }.andReturn()

                Then("[S-05] 404 Not Found가 반환된다") {
                    result.response.status shouldBe 404
                }
            }
        }
    }
}
