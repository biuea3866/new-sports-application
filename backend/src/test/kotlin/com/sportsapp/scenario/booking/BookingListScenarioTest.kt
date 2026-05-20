package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class BookingListScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val restTemplate: TestRestTemplate,
) : BaseIntegrationTest() {

    private fun headers(userId: Long): HttpHeaders = HttpHeaders().apply {
        set("X-User-Id", userId.toString())
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[S-01] userId=1의 CONFIRMED Booking이 존재할 때") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 10,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)
            bookingDomainService.confirmBooking(pending.id, paymentId = 99L)

            When("GET /bookings/me?status=CONFIRMED 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/me?status=CONFIRMED",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers(1L)),
                    String::class.java,
                )

                Then("[S-01] 200 응답과 CONFIRMED 상태 bookings가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "CONFIRMED"
                    response.body shouldContain "totalElements"
                }
            }
        }

        Given("[S-02a] 본인(userId=1)의 Booking 단건 조회") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-02",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 5,
                )
            )
            val booking = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)

            When("본인(userId=1)이 GET /bookings/{id} 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${booking.id}",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers(1L)),
                    String::class.java,
                )

                Then("[S-02] 200 응답과 Booking 정보가 반환된다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "PENDING"
                }
            }
        }

        Given("[S-02b] 타인(userId=2)이 userId=1의 Booking 단건 조회 시도") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-03",
                    date = ZonedDateTime.now(),
                    timeRange = "11:00-12:00",
                    capacity = 5,
                )
            )
            val booking = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)

            When("타인(userId=2)이 GET /bookings/{id} 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${booking.id}",
                    HttpMethod.GET,
                    HttpEntity<Void>(headers(2L)),
                    String::class.java,
                )

                Then("[S-02] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }
    }
}
