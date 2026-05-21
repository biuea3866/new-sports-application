package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class BookingConfirmScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("CONFIRMED 상태의 Booking이 이미 존재하는 상태") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 10,
                    ownerId = 1L,
                )
            )
            val confirmed = bookingDomainService.createPendingBooking(
                userId = 1L,
                slotId = slot.id,
            ).let { bookingDomainService.confirmBooking(it.id, paymentId = 100L) }

            When("동일한 Booking에 confirm을 재호출하면") {
                val result = bookingDomainService.confirmBooking(confirmed.id, paymentId = 200L)

                Then("[S-01] paymentId가 변경되지 않고 멱등하게 처리된다") {
                    result.status shouldBe BookingStatus.CONFIRMED
                    result.paymentId shouldBe 100L
                }
            }
        }
    }
}
