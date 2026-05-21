package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.ListNoShowBookingsCommand
import com.sportsapp.application.booking.ListNoShowBookingsUseCase
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import com.sportsapp.infrastructure.persistence.booking.BookingJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class ListNoShowBookingsScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val listNoShowBookingsUseCase: ListNoShowBookingsUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun createSlotAndNoShowBooking(facilityId: String, ownerId: Long, userId: Long): Booking {
        val slot = slotRepository.save(
            Slot.create(
                facilityId = facilityId,
                date = ZonedDateTime.now(),
                timeRange = "09:00-10:00",
                capacity = 10,
                ownerId = ownerId,
            )
        )
        val booking = bookingDomainService.createPendingBooking(userId = userId, slotId = slot.id)
        bookingDomainService.confirmBooking(booking.id, paymentId = 99L)
        val confirmed = bookingJpaRepository.findById(booking.id).get()
        confirmed.markNoShow()
        return bookingJpaRepository.save(confirmed)
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[S-01] 노쇼 booking 2건이 DB에 있을 때") {
            createSlotAndNoShowBooking("FAC-01", 1L, 10L)
            createSlotAndNoShowBooking("FAC-01", 1L, 11L)

            When("[S-01] UseCase 실행 시") {
                val command = ListNoShowBookingsCommand(
                    operatorUserId = 1L,
                    facilityId = null,
                    from = ZonedDateTime.now().minusDays(1),
                    to = ZonedDateTime.now().plusDays(1),
                    pageable = PageRequest.of(0, 20),
                )
                val response = listNoShowBookingsUseCase.execute(command)

                Then("[S-01] 2건이 반환된다") {
                    response.totalElements shouldBe 2L
                    response.bookings.size shouldBe 2
                    response.bookings.all { it.status == BookingStatus.NO_SHOW } shouldBe true
                }
            }
        }
    }
}
