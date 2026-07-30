package com.sportsapp.infrastructure.booking.mysql

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookingOrderQueryRepositoryImplTest(
    @Autowired private val bookingOrderQueryRepository: BookingOrderQueryRepository,
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
) : BaseIntegrationTest() {

    // 정오(UTC) 고정 — 자정 근처 시각은 저장/조회 왕복 시 타임존 변환으로 날짜가 하루 밀릴 수 있어 회피한다.
    private fun createSlot(facilityId: String = "FAC-01", timeRange: String = "09:00-10:00"): Slot =
        slotJpaRepository.save(
            Slot.create(
                facilityId = facilityId,
                date = ZonedDateTime.of(2026, 7, 10, 12, 0, 0, 0, ZoneOffset.UTC),
                timeRange = timeRange,
                capacity = 10,
                ownerId = 1L,
            )
        )

    private fun createBooking(slotId: Long, userId: Long): Booking =
        bookingJpaRepository.save(Booking.createPending(userId = userId, slotId = slotId))

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("사용자별 Booking과 참조 Slot이 정상 존재할 때") {
            val slot = createSlot()
            val booking = createBooking(slot.id, 42L)

            When("findByUserId로 조회하면") {
                val results = bookingOrderQueryRepository.findByUserId(42L)

                Then("date timeRange 시설 예약 라벨(title)을 포함해 반환한다") {
                    results shouldHaveSize 1
                    results[0].bookingId shouldBe booking.id
                    results[0].title shouldBe "2026-07-10 09:00-10:00 시설 예약"
                }

                Then("title이 BOOKING #id 형태의 기술 식별자를 포함하지 않는다") {
                    results[0].title shouldNotContain "BOOKING"
                    results[0].title shouldNotContain "#"
                }
            }
        }

        Given("참조 Slot이 소프트 삭제된 Booking이 존재할 때") {
            val slot = createSlot(facilityId = "FAC-DELETED")
            val booking = createBooking(slot.id, 43L)
            slot.softDelete(1L)
            slotJpaRepository.save(slot)

            When("findByUserId로 조회하면") {
                val results = bookingOrderQueryRepository.findByUserId(43L)

                Then("기본 라벨(시설 예약)로 방어 반환한다") {
                    results shouldHaveSize 1
                    results[0].bookingId shouldBe booking.id
                    results[0].title shouldBe "시설 예약"
                }
            }
        }

        Given("참조 Slot 자체가 부재(고아 참조)인 Booking이 존재할 때") {
            val slot = createSlot(facilityId = "FAC-ORPHAN")
            val orphanSlotId = slot.id + 999_999L
            val booking = bookingJpaRepository.save(Booking.createPending(userId = 44L, slotId = orphanSlotId))

            When("findByUserId로 조회하면") {
                val results = bookingOrderQueryRepository.findByUserId(44L)

                Then("기본 라벨(시설 예약)로 방어 반환한다") {
                    results shouldHaveSize 1
                    results[0].bookingId shouldBe booking.id
                    results[0].title shouldBe "시설 예약"
                }
            }
        }

        Given("여러 사용자의 Booking이 섞여 있을 때") {
            val slot = createSlot(facilityId = "FAC-MULTI")
            createBooking(slot.id, 50L)
            createBooking(slot.id, 51L)

            When("특정 userId로만 조회하면") {
                val results = bookingOrderQueryRepository.findByUserId(50L)

                Then("해당 사용자의 예약만 반환된다") {
                    results shouldHaveSize 1
                    results[0].userId shouldBe 50L
                }
            }
        }

        Given("조회 대상 사용자의 Booking이 하나도 없을 때") {
            When("findByUserId로 조회하면") {
                val results = bookingOrderQueryRepository.findByUserId(999L)

                Then("빈 목록이 반환된다") {
                    results.shouldBeEmpty()
                }
            }
        }

        Given("기존 findPageByUserId 조회 대상 데이터가 존재할 때 (회귀)") {
            val slot = createSlot(facilityId = "FAC-REGRESSION")
            createBooking(slot.id, 60L)

            When("기존 페이징 조회를 호출하면") {
                val page = bookingJpaRepository.findPageByUserId(
                    userId = 60L,
                    status = null,
                    pageable = org.springframework.data.domain.PageRequest.of(0, 20),
                )

                Then("기존 예약 조회 동작이 불변이다") {
                    page.totalElements shouldBe 1L
                    page.content shouldHaveSize 1
                }
            }
        }
    }
}
