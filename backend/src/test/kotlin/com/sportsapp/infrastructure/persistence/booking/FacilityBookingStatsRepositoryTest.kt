package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingRepository
import com.sportsapp.domain.booking.Slot
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class FacilityBookingStatsRepositoryTest(
    @Autowired private val bookingRepository: BookingRepository,
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseJpaIntegrationTest() {

    private fun createSlot(facilityId: String, ownerId: Long = 1L): Slot =
        slotJpaRepository.save(
            Slot.create(
                facilityId = facilityId,
                date = ZonedDateTime.now().plusDays(1),
                timeRange = "10:00-11:00",
                capacity = 10,
                ownerId = ownerId,
            ),
        )

    private fun createConfirmedBooking(slotId: Long, userId: Long = 1L): Booking {
        val booking = bookingRepository.save(Booking.createPending(userId = userId, slotId = slotId))
        booking.confirm(paymentId = 0L)
        return bookingRepository.save(booking)
    }

    private fun createNoShowBooking(slotId: Long, userId: Long = 1L): Booking {
        val booking = bookingRepository.save(Booking.createPending(userId = userId, slotId = slotId))
        booking.confirm(paymentId = 0L)
        bookingRepository.save(booking)
        jdbcTemplate.update("UPDATE bookings SET status = 'NO_SHOW' WHERE id = ?", booking.id)
        return requireNotNull(bookingRepository.findById(booking.id)) { "booking must exist after save" }
    }

    private val from = ZonedDateTime.now().minusDays(1)
    private val to = ZonedDateTime.now().plusDays(1)

    init {
        Given("[R-02] facilityId=fac-stat-001에 confirmed booking 2건 + no_show 1건이 있는 상태") {
            val slot1 = createSlot("fac-stat-001")
            val slot2 = createSlot("fac-stat-001")
            createConfirmedBooking(slot1.id)
            createConfirmedBooking(slot2.id)
            createNoShowBooking(slot1.id, userId = 2L)

            When("[R-02] aggregateStatsByFacilityIds 호출 시") {
                val result = bookingRepository.aggregateStatsByFacilityIds(
                    listOf("fac-stat-001"),
                    from,
                    to,
                )

                Then("[R-02] totalBookings=3, noShowCount=1이 반환된다") {
                    result shouldHaveSize 1
                    val stats = result[0]
                    stats.facilityId shouldBe "fac-stat-001"
                    stats.totalBookings shouldBe 3L
                    stats.noShowCount shouldBe 1L
                }
            }
        }

        Given("[R-02] 빈 facilityIds 리스트로 조회") {
            When("[R-02] aggregateStatsByFacilityIds(emptyList()) 호출 시") {
                val result = bookingRepository.aggregateStatsByFacilityIds(emptyList(), from, to)

                Then("[R-02] 빈 결과가 반환된다") {
                    result shouldHaveSize 0
                }
            }
        }

        Given("[R-02] 조회 기간 외 booking은 집계에서 제외") {
            val slot = createSlot("fac-stat-002")
            createConfirmedBooking(slot.id)

            When("[R-02] 미래 기간으로 조회 시") {
                val futureFrom = ZonedDateTime.now().plusDays(10)
                val futureTo = ZonedDateTime.now().plusDays(20)
                val result = bookingRepository.aggregateStatsByFacilityIds(
                    listOf("fac-stat-002"),
                    futureFrom,
                    futureTo,
                )

                Then("[R-02] 결과가 비어 있다") {
                    result shouldHaveSize 0
                }
            }
        }
    }
}
