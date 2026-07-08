package com.sportsapp.presentation.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class BookingCancelApiTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingRepository: BookingRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val restTemplate: TestRestTemplate,
) : BaseIntegrationTest() {

    private fun headers(userId: Long): HttpHeaders = HttpHeaders().apply {
        set("X-User-Id", userId.toString())
        contentType = MediaType.APPLICATION_JSON
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[S-03] PENDING 상태 booking을 본인이 취소 요청") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CANCEL-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 10,
                    ownerId = 1L,
                )
            )
            val booking = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)

            When("POST /bookings/{id}/cancel 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${booking.id}/cancel",
                    HttpMethod.POST,
                    HttpEntity<String>("{}", headers(1L)),
                    String::class.java,
                )

                Then("[S-03] 200 응답과 status=CANCELLED가 반환되고 DB도 CANCELLED") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "CANCELLED"
                    val updatedBooking = bookingRepository.findById(booking.id)
                    updatedBooking?.status shouldBe BookingStatus.CANCELLED
                }
            }
        }

        Given("[S-04] CONFIRMED 상태 booking을 본인이 취소 요청 (슬롯 점유 해제 검증)") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CANCEL-02",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 2,
                    ownerId = 1L,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)
            bookingDomainService.confirmBooking(pending.id, paymentId = 200L)

            When("POST /bookings/{id}/cancel 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${pending.id}/cancel",
                    HttpMethod.POST,
                    HttpEntity<String>("{}", headers(1L)),
                    String::class.java,
                )

                Then("[S-04] 200 응답이며 CANCELLED 후 슬롯 활성 예약 수가 감소한다") {
                    response.statusCode shouldBe HttpStatus.OK
                    response.body shouldContain "CANCELLED"
                    val activeCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM bookings WHERE slot_id = ? AND status IN ('PENDING','CONFIRMED')",
                        Long::class.java,
                        slot.id,
                    )
                    activeCount shouldBe 0L
                }
            }
        }

        Given("[S-05] 다른 사용자(userId=99)가 userId=1의 booking 취소 시도") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CANCEL-03",
                    date = ZonedDateTime.now(),
                    timeRange = "11:00-12:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            val booking = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)

            When("userId=99로 POST /bookings/{id}/cancel 호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${booking.id}/cancel",
                    HttpMethod.POST,
                    HttpEntity<String>("{}", headers(99L)),
                    String::class.java,
                )

                Then("[S-05] 403 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.FORBIDDEN
                }
            }
        }

        Given("[S-06] 이미 CANCELLED 상태의 booking을 재취소 시도") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-CANCEL-04",
                    date = ZonedDateTime.now(),
                    timeRange = "12:00-13:00",
                    capacity = 5,
                    ownerId = 2L,
                )
            )
            val booking = bookingDomainService.createPendingBooking(userId = 2L, slotId = slot.id)
            bookingDomainService.cancel(booking.id, cancelledByUserId = 2L, reason = null)

            When("POST /bookings/{id}/cancel 재호출 시") {
                val response = restTemplate.exchange(
                    "/bookings/${booking.id}/cancel",
                    HttpMethod.POST,
                    HttpEntity<String>("{}", headers(2L)),
                    String::class.java,
                )

                Then("[S-06] 422 응답이 반환된다") {
                    response.statusCode shouldBe HttpStatus.UNPROCESSABLE_ENTITY
                }
            }
        }
    }
}
