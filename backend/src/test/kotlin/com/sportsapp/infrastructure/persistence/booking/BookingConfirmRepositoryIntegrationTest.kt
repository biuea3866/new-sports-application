package com.sportsapp.infrastructure.persistence.booking

import com.sportsapp.BaseJpaIntegrationTest
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime
import com.sportsapp.infrastructure.booking.mysql.SlotJpaRepository
import com.sportsapp.infrastructure.booking.mysql.BookingJpaRepository

class BookingConfirmRepositoryIntegrationTest(
    @Autowired private val slotJpaRepository: SlotJpaRepository,
    @Autowired private val bookingJpaRepository: BookingJpaRepository,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionTemplate: TransactionTemplate,
) : BaseJpaIntegrationTest() {

    private fun createSlot(capacity: Int = 5): Slot = slotJpaRepository.save(
        Slot.create(
            facilityId = "FAC-01",
            date = ZonedDateTime.now().plusDays(1),
            timeRange = "09:00-10:00",
            capacity = capacity,
            ownerId = 1L,
        )
    )

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("PENDING Booking 저장 후 confirm") {
            val slot = createSlot()
            val booking = bookingJpaRepository.save(Booking.createPending(userId = 1L, slotId = slot.id))

            When("confirm(paymentId) 후 save하면") {
                booking.confirm(paymentId = 42L)
                booking.pullDomainEvents()
                bookingJpaRepository.save(booking)

                Then("status=CONFIRMED, paymentId가 UTC로 정확히 저장되고 findById로 복원된다") {
                    val found = bookingJpaRepository.findById(booking.id).orElseThrow()
                    found.status shouldBe BookingStatus.CONFIRMED
                    found.paymentId shouldBe 42L
                }
            }
        }

        Given("슬롯에 PENDING과 CANCELLED Booking이 혼재할 때") {
            val slot = createSlot()
            bookingJpaRepository.save(Booking.createPending(userId = 1L, slotId = slot.id))
            bookingJpaRepository.save(Booking.createPending(userId = 2L, slotId = slot.id))
            val cancelled = bookingJpaRepository.save(Booking.createPending(userId = 3L, slotId = slot.id))
            cancelled.cancel()
            bookingJpaRepository.save(cancelled)

            When("countBySlotIdAndStatusIn([PENDING, CONFIRMED])을 호출하면") {
                val count = bookingJpaRepository.countBySlotIdAndStatusIn(
                    slot.id,
                    listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED),
                )

                Then("PENDING 2건만 집계되고 CANCELLED는 포함되지 않는다") {
                    count shouldBe 2L
                }
            }
        }

        Given("capacity=1인 슬롯에 findForUpdateById 호출 시") {
            val slot = createSlot(capacity = 1)

            When("findForUpdateById로 슬롯을 조회하면") {
                val found = transactionTemplate.execute {
                    slotJpaRepository.findForUpdateById(slot.id)
                }

                Then("정확한 슬롯이 반환되고 capacity가 일치한다") {
                    found?.id shouldBe slot.id
                    found?.capacity shouldBe 1
                }
            }
        }
    }
}
