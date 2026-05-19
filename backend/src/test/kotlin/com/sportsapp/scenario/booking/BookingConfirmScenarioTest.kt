package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.infrastructure.persistence.booking.BookingJpaEntity
import com.sportsapp.infrastructure.persistence.booking.BookingJpaRepository
import com.sportsapp.infrastructure.persistence.booking.SlotJpaEntity
import com.sportsapp.infrastructure.persistence.booking.SlotJpaRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

class BookingConfirmScenarioTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
) : BaseIntegrationTest() {

    init {
        afterEach {
            bookingJpaRepository.deleteAll()
            slotJpaRepository.deleteAll()
        }

        Given("CONFIRMED 상태의 Booking이 이미 존재하는 상태") {
            val slot = slotJpaRepository.save(
                SlotJpaEntity(
                    id = 0L,
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 10,
                )
            )
            val savedBooking = bookingJpaRepository.save(
                BookingJpaEntity(
                    id = 0L,
                    userId = 1L,
                    slotId = slot.id,
                    status = BookingStatus.CONFIRMED,
                    paymentId = 100L,
                    createdAt = ZonedDateTime.now(),
                )
            )

            When("동일한 Booking에 confirm을 재호출하면") {
                val booking = bookingJpaRepository.findById(savedBooking.id).get().toDomain()
                booking.confirm(paymentId = 200L)
                val result = bookingJpaRepository.save(BookingJpaEntity.fromDomain(booking)).toDomain()

                Then("[S-01] paymentId가 변경되지 않고 멱등하게 처리된다") {
                    result.status shouldBe BookingStatus.CONFIRMED
                    result.paymentId shouldBe 100L
                }
            }
        }
    }
}
