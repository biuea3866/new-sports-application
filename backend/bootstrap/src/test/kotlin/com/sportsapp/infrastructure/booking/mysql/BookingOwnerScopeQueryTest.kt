package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.BookingRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * 파트너(시설 소유자) 스코프 예약 조회.
 *
 * 포털 "예약 관리"는 파트너가 **소유한 시설의 슬롯에 걸린** 예약을 봐야 한다. 기존 화면은
 * 예약자 스코프(`/bookings/me`)를 호출해 파트너 본인이 예약한 건만 찾았고, 그래서 소유 시설에
 * 예약이 실재하는데도 0건으로 보였다.
 *
 * 소유권 판정은 `slots.owner_id`로 하므로 facility 컨텍스트를 참조하지 않는다.
 * **다른 파트너의 예약이 절대 섞이면 안 된다** — 권한 누수이므로 격리 케이스를 반드시 검증한다.
 */
class BookingOwnerScopeQueryTest(
    @Autowired private val bookingRepository: BookingRepository,
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    private val partnerOwnerId = 69L
    private val otherOwnerId = 70L

    // 정오(UTC) 고정 — 자정 근처는 저장/조회 왕복 시 날짜가 하루 밀릴 수 있어 회피한다.
    private fun createSlot(ownerId: Long, facilityId: String, timeRange: String): Slot =
        slotJpaRepository.save(
            Slot.create(
                facilityId = facilityId,
                date = ZonedDateTime.of(2026, 8, 10, 12, 0, 0, 0, ZoneOffset.UTC),
                timeRange = timeRange,
                capacity = 10,
                ownerId = ownerId,
            )
        )

    private fun createBooking(slotId: Long, bookerUserId: Long): Booking =
        bookingJpaRepository.save(Booking.createPending(userId = bookerUserId, slotId = slotId))

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("내 시설 슬롯에 다른 사람이 건 예약이 있을 때") {
            val mySlot = createSlot(partnerOwnerId, "FAC-MINE", "09:00-10:00")
            val booking = createBooking(mySlot.id, bookerUserId = 68L)

            When("파트너 스코프로 예약을 조회하면") {
                val result = bookingRepository.findPageByOwnerUserId(partnerOwnerId, null, PageRequest.of(0, 20))

                Then("예약자가 내가 아니어도 내 시설 예약이므로 조회된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe booking.id
                }
            }
        }

        Given("다른 파트너 시설의 예약만 있을 때") {
            val otherSlot = createSlot(otherOwnerId, "FAC-OTHER", "11:00-12:00")
            createBooking(otherSlot.id, bookerUserId = 68L)

            When("파트너 스코프로 예약을 조회하면") {
                val result = bookingRepository.findPageByOwnerUserId(partnerOwnerId, null, PageRequest.of(0, 20))

                // 권한 누수 방지 — 남의 시설 예약이 한 건이라도 새면 안 된다.
                Then("다른 파트너의 예약은 제외된다") {
                    result.totalElements shouldBe 0L
                    result.content.shouldBeEmpty()
                }
            }
        }

        Given("내 시설과 다른 파트너 시설 예약이 섞여 있을 때") {
            val mySlot = createSlot(partnerOwnerId, "FAC-MINE", "09:00-10:00")
            val otherSlot = createSlot(otherOwnerId, "FAC-OTHER", "11:00-12:00")
            val myBooking = createBooking(mySlot.id, bookerUserId = 68L)
            createBooking(otherSlot.id, bookerUserId = 68L)

            When("파트너 스코프로 예약을 조회하면") {
                val result = bookingRepository.findPageByOwnerUserId(partnerOwnerId, null, PageRequest.of(0, 20))

                Then("내 시설 예약만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe myBooking.id
                }
            }
        }

        Given("내 시설에 PENDING·CONFIRMED 예약이 섞여 있고 상태로 필터할 때") {
            val mySlot = createSlot(partnerOwnerId, "FAC-MINE", "09:00-10:00")
            createBooking(mySlot.id, bookerUserId = 68L)
            val confirmedBooking = createBooking(mySlot.id, bookerUserId = 71L)
            confirmedBooking.confirm(paymentId = 500L)
            bookingJpaRepository.save(confirmedBooking)

            When("CONFIRMED 상태로 필터하면") {
                val result = bookingRepository.findPageByOwnerUserId(
                    partnerOwnerId,
                    BookingStatus.CONFIRMED,
                    PageRequest.of(0, 20),
                )

                Then("해당 상태 예약만 반환된다") {
                    result.totalElements shouldBe 1L
                    result.content.first().id shouldBe confirmedBooking.id
                }
            }
        }

        Given("내 시설에 PENDING·CONFIRMED 예약이 섞여 있고 필터가 없을 때") {
            val mySlot = createSlot(partnerOwnerId, "FAC-MINE", "09:00-10:00")
            val pendingBooking = createBooking(mySlot.id, bookerUserId = 68L)
            val confirmedBooking = createBooking(mySlot.id, bookerUserId = 71L)
            confirmedBooking.confirm(paymentId = 500L)
            bookingJpaRepository.save(confirmedBooking)

            When("상태 필터 없이 조회하면") {
                val result = bookingRepository.findPageByOwnerUserId(partnerOwnerId, null, PageRequest.of(0, 20))

                Then("모든 상태의 예약이 반환된다") {
                    result.totalElements shouldBe 2L
                    result.content.map { it.id }.toSet() shouldBe setOf(pendingBooking.id, confirmedBooking.id)
                }
            }
        }

        Given("내 시설에 예약이 없을 때") {
            createSlot(partnerOwnerId, "FAC-MINE", "09:00-10:00")

            When("파트너 스코프로 예약을 조회하면") {
                val result = bookingRepository.findPageByOwnerUserId(partnerOwnerId, null, PageRequest.of(0, 20))

                Then("빈 목록이 반환된다") {
                    result.totalElements shouldBe 0L
                    result.content.shouldBeEmpty()
                }
            }
        }
    }
}
