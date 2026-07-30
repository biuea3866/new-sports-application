package com.sportsapp.scenario.ticketing

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.ticketing.entity.Event
import com.sportsapp.domain.ticketing.entity.EventStatus
import com.sportsapp.domain.ticketing.entity.Seat
import com.sportsapp.infrastructure.ticketing.mysql.EventJpaRepository
import com.sportsapp.infrastructure.ticketing.mysql.SeatJpaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import java.math.BigDecimal
import java.time.ZoneOffset
import java.time.ZonedDateTime

class EventQueryScenarioTest(
    @Autowired private val restTemplate: TestRestTemplate,
    @Autowired private val eventJpaRepository: EventJpaRepository,
    @Autowired private val seatJpaRepository: SeatJpaRepository,
) : BaseIntegrationTest() {

    private val baseTime = ZonedDateTime.of(2026, 12, 1, 18, 0, 0, 0, ZoneOffset.UTC)

    init {
        Given("Event 목록 조회 시나리오") {
            val event1 = eventJpaRepository.save(Event(0L, "Open Event", "Seoul", baseTime, EventStatus.OPEN, 1L))
            eventJpaRepository.save(Event(0L, "Scheduled Event", "Busan", baseTime.plusMonths(1), EventStatus.SCHEDULED, 1L))

            When("[S-01] GET /events?status=OPEN 요청 시") {
                val response = restTemplate.getForEntity(
                    "/events?status=OPEN&page=0&size=10",
                    String::class.java
                )

                Then("200 응답과 OPEN 이벤트가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "Open Event"
                }
            }

            When("[S-02] 인증 없이 GET /events 요청 시") {
                val response = restTemplate.getForEntity(
                    "/events?page=0&size=10",
                    String::class.java
                )

                Then("200 응답을 받는다") {
                    response.statusCode shouldBe HttpStatus.OK
                }
            }

            When("[S-03] GET /events/{id} 단건 조회 시") {
                seatJpaRepository.save(Seat(0L, event1.id, "A", "1", "1", BigDecimal("50000")))
                seatJpaRepository.save(Seat(0L, event1.id, "A", "1", "2", BigDecimal("50000")))

                val response = restTemplate.getForEntity(
                    "/events/${event1.id}",
                    String::class.java
                )

                Then("200 응답, 섹션 정보·seats 배열·ZonedDateTime ISO 8601 형식(startsAt)이 포함된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "Open Event"
                    response.body shouldContain "sections"
                    response.body shouldContain "totalSeats"
                    response.body shouldContain "seats"
                    response.body shouldContain "available"
                    response.body shouldContain "rowNo"
                    response.body shouldContain "seatNo"
                    response.body shouldContain "2026-12-01T18:00:00"
                }
            }

            When("[S-04] 존재하지 않는 ID로 GET /events/{id} 요청 시") {
                val response = restTemplate.getForEntity(
                    "/events/999999",
                    String::class.java
                )

                Then("404 응답을 받는다") {
                    response.statusCode shouldBe HttpStatus.NOT_FOUND
                }
            }
        }
    }
}
