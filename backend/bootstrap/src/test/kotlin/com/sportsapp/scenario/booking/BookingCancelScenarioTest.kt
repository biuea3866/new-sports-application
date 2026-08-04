package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.dto.CancelBookingCommand
import com.sportsapp.application.booking.usecase.CancelBookingUseCase
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.exception.InvalidBookingStateException
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.time.ZonedDateTime

class BookingCancelScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val cancelBookingUseCase: CancelBookingUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
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
            // confirmBooking()은 QueryDSL 벌크 UPDATE(BookingRepository.tryConfirm)를 호출해
            // 활성 트랜잭션이 필요하다(BookingDomainService에는 @Transactional이 없다 —
            // 트랜잭션 경계는 UseCase가 소유하는 컨벤션). 테스트에서 UseCase를 거치지 않고
            // 직접 호출하므로 TransactionTemplate으로 명시적으로 트랜잭션을 열어준다.
            val confirmed = requireNotNull(
                TransactionTemplate(transactionManager).execute {
                    bookingDomainService.confirmBooking(pending.id, paymentId = 100L)
                }
            )
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
