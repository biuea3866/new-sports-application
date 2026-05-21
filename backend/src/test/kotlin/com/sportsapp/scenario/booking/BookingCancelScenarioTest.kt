package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.CancelBookingCommand
import com.sportsapp.application.booking.CancelBookingUseCase
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.InvalidBookingStateException
import com.sportsapp.domain.booking.Slot
import com.sportsapp.domain.booking.SlotRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class BookingCancelScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val cancelBookingUseCase: CancelBookingUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[S-01] CONFIRMED 상태 booking에 취소 요청") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "09:00-10:00",
                    capacity = 10,
                    ownerId = 1L,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)
            val confirmed = bookingDomainService.confirmBooking(pending.id, paymentId = 100L)
            val command = CancelBookingCommand(
                bookingId = confirmed.id,
                cancelledByUserId = 1L,
                reason = "일정 취소",
            )

            When("CancelBookingUseCase를 실행하면") {
                val result = cancelBookingUseCase.execute(command)

                Then("[S-01] DB status=CANCELLED로 반영된다") {
                    result.status shouldBe BookingStatus.CANCELLED
                    val domainResult = bookingDomainService.getBooking(1L, confirmed.id)
                    domainResult.status shouldBe BookingStatus.CANCELLED
                }
            }
        }

        Given("[S-02] 이미 CANCELLED 상태의 booking") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-01",
                    date = ZonedDateTime.now(),
                    timeRange = "10:00-11:00",
                    capacity = 10,
                    ownerId = 2L,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 2L, slotId = slot.id)
            bookingDomainService.cancel(pending.id, cancelledByUserId = 2L, reason = null)
            val command = CancelBookingCommand(
                bookingId = pending.id,
                cancelledByUserId = 2L,
                reason = null,
            )

            When("재취소를 시도하면") {
                Then("[S-02] InvalidBookingStateException이 발생한다") {
                    shouldThrow<InvalidBookingStateException> {
                        cancelBookingUseCase.execute(command)
                    }
                }
            }
        }
    }
}
