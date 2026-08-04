package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

class BookingConfirmScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("CONFIRMED 상태의 Booking이 이미 존재하는 상태") {
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
            // confirmBooking()은 QueryDSL 벌크 UPDATE(BookingRepository.tryConfirm)를 호출해
            // 활성 트랜잭션이 필요하다(BookingDomainService에는 @Transactional이 없다 —
            // 트랜잭션 경계는 UseCase가 소유하는 컨벤션). 테스트에서 UseCase를 거치지 않고
            // 직접 호출하므로 TransactionTemplate으로 명시적으로 트랜잭션을 열어준다.
            val confirmed = requireNotNull(
                TransactionTemplate(transactionManager).execute {
                    bookingDomainService.confirmBooking(pending.id, paymentId = 100L)
                }
            )

            When("동일한 Booking에 confirm을 재호출하면") {
                val result = requireNotNull(
                    TransactionTemplate(transactionManager).execute {
                        bookingDomainService.confirmBooking(confirmed.id, paymentId = 200L)
                    }
                )

                Then("[S-01] paymentId가 변경되지 않고 멱등하게 처리된다") {
                    result.status shouldBe BookingStatus.CONFIRMED
                    result.paymentId shouldBe 100L
                }
            }
        }
    }
}
