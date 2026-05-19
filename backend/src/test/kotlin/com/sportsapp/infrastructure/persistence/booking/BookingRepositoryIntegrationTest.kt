package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.BookingStatus
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import java.time.ZonedDateTime

class BookingRepositoryIntegrationTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
) : BaseIntegrationTest() {

    private fun createSlot(): SlotJpaEntity = slotJpaRepository.save(
        SlotJpaEntity(
            id = 0L,
            facilityId = "FAC-01",
            date = ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 10,
        )
    )

    private fun createBooking(slotId: Long, userId: Long, status: BookingStatus): BookingJpaEntity =
        bookingJpaRepository.save(
            BookingJpaEntity(
                id = 0L,
                userId = userId,
                slotId = slotId,
                status = status,
                paymentId = null,
                createdAt = ZonedDateTime.now(),
            )
        )

    init {
        afterEach {
            bookingJpaRepository.deleteAll()
            slotJpaRepository.deleteAll()
        }

        Given("[R-01] PENDING Booking 저장 후 userId + status 조회") {
            val slot = createSlot()
            val booking = createBooking(slot.id, 42L, BookingStatus.PENDING)

            When("userId + PENDING 조건으로 조회하면") {
                val results = bookingJpaRepository.findAllByUserIdAndStatus(42L, BookingStatus.PENDING)

                Then("[R-01] 정확히 1건이 반환된다") {
                    results shouldHaveSize 1
                    results[0].id shouldBe booking.id
                    results[0].status shouldBe BookingStatus.PENDING
                }
            }
        }

        Given("[R-03] PENDING Booking 저장 후 동적 쿼리 조회") {
            val slot = createSlot()
            createBooking(slot.id, 42L, BookingStatus.PENDING)

            When("userId + status 조건만으로 동적 쿼리를 호출하면") {
                val results = bookingJpaRepository.findByUserIdAndStatusAndDateRange(
                    userId = 42L,
                    status = BookingStatus.PENDING,
                    from = null,
                    to = null,
                )

                Then("[R-03] 동적 조건 쿼리가 정확한 결과와 카운트를 반환한다") {
                    results shouldHaveSize 1
                    results[0].userId shouldBe 42L
                    results[0].status shouldBe BookingStatus.PENDING
                }
            }

            When("존재하지 않는 userId로 조회하면") {
                val results = bookingJpaRepository.findByUserIdAndStatusAndDateRange(
                    userId = 999L,
                    status = null,
                    from = null,
                    to = null,
                )

                Then("[R-03] 카운트 0건이 반환된다") {
                    results shouldHaveSize 0
                }
            }
        }
    }
}
