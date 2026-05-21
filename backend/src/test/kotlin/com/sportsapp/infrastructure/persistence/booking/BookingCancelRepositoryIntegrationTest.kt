package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class BookingCancelRepositoryIntegrationTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[R-01] PENDING Booking 저장 후 cancel + save") {
            val slot = slotJpaRepository.save(
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            val booking = bookingJpaRepository.save(
                Booking.createPending(userId = 1L, slotId = slot.id)
            )
            val updatedAtBefore = booking.updatedAt

            When("cancel() 후 save하면") {
                booking.cancel(cancelledByUserId = 1L, reason = "변경")
                val saved = bookingJpaRepository.save(booking)

                Then("[R-01] DB에서 조회 시 status=CANCELLED, updated_at이 갱신된다") {
                    val found = bookingJpaRepository.findById(saved.id).orElseThrow()
                    found.status shouldBe BookingStatus.CANCELLED
                    found.updatedAt shouldNotBe updatedAtBefore
                }
            }
        }
    }
}
