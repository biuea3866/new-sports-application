package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingStatus
import com.sportsapp.domain.booking.Slot
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZonedDateTime

class NoShowBookingRepositoryTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private fun createSlot(facilityId: String, ownerId: Long): Slot = slotJpaRepository.save(
        Slot.create(
            facilityId = facilityId,
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 10,
            ownerId = ownerId,
        )
    )

    private fun createNoShowBooking(slotId: Long, userId: Long): Booking {
        val booking = Booking.createPending(userId = userId, slotId = slotId)
        val saved = bookingJpaRepository.save(booking)
        saved.confirm(paymentId = 99L)
        val confirmed = bookingJpaRepository.save(saved)
        confirmed.markNoShow()
        return bookingJpaRepository.save(confirmed)
    }

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("[R-01] 운영자 ownerId=1의 시설에 노쇼 예약 2건 존재") {
            val slot = createSlot(facilityId = "FAC-01", ownerId = 1L)
            createNoShowBooking(slot.id, 10L)
            createNoShowBooking(slot.id, 11L)

            val from = ZonedDateTime.now().minusDays(1)
            val to = ZonedDateTime.now().plusDays(1)

            When("[R-01] listNoShows 쿼리 실행 시") {
                val result = bookingJpaRepository.findNoShowsByOwnerAndPeriod(
                    ownerUserId = 1L,
                    facilityId = null,
                    from = from,
                    to = to,
                    pageable = PageRequest.of(0, 20),
                )

                Then("[R-01] status=NO_SHOW + facility_owner_id 필터로 createdAt desc 페이징을 반환한다") {
                    result.totalElements shouldBe 2L
                    result.content shouldHaveSize 2
                    result.content.all { it.status == BookingStatus.NO_SHOW } shouldBe true
                }
            }
        }

        Given("[R-01b] 다른 운영자(ownerId=2)의 노쇼 예약은 필터링되는지") {
            val slotOwner1 = createSlot(facilityId = "FAC-01", ownerId = 1L)
            val slotOwner2 = createSlot(facilityId = "FAC-02", ownerId = 2L)
            createNoShowBooking(slotOwner1.id, 10L)
            createNoShowBooking(slotOwner2.id, 11L)

            val from = ZonedDateTime.now().minusDays(1)
            val to = ZonedDateTime.now().plusDays(1)

            When("[R-01b] ownerId=1로 조회 시") {
                val result = bookingJpaRepository.findNoShowsByOwnerAndPeriod(
                    ownerUserId = 1L,
                    facilityId = null,
                    from = from,
                    to = to,
                    pageable = PageRequest.of(0, 20),
                )

                Then("[R-01b] ownerId=1의 노쇼 예약 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content[0].userId shouldBe 10L
                }
            }
        }

        Given("[R-01c] facilityId 필터를 적용하는 경우") {
            val slot1 = createSlot(facilityId = "FAC-01", ownerId = 1L)
            val slot2 = createSlot(facilityId = "FAC-02", ownerId = 1L)
            createNoShowBooking(slot1.id, 10L)
            createNoShowBooking(slot2.id, 11L)

            val from = ZonedDateTime.now().minusDays(1)
            val to = ZonedDateTime.now().plusDays(1)

            When("[R-01c] facilityId=FAC-01 필터로 조회 시") {
                val result = bookingJpaRepository.findNoShowsByOwnerAndPeriod(
                    ownerUserId = 1L,
                    facilityId = "FAC-01",
                    from = from,
                    to = to,
                    pageable = PageRequest.of(0, 20),
                )

                Then("[R-01c] FAC-01의 노쇼 1건만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content[0].slotId shouldBe slot1.id
                }
            }
        }
    }
}
