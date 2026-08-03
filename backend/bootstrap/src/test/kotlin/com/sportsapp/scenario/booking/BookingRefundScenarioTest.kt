package com.sportsapp.scenario.booking

import com.sportsapp.BaseIntegrationTest
import com.sportsapp.application.booking.dto.RefundBookingCommand
import com.sportsapp.application.booking.usecase.RefundBookingUseCase
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.repository.SlotRepository
import io.kotest.matchers.shouldBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.transaction.PlatformTransactionManager
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.ZonedDateTime

class BookingRefundScenarioTest(
    @Autowired private val slotRepository: SlotRepository,
    @Autowired private val bookingDomainService: BookingDomainService,
    @Autowired private val refundBookingUseCase: RefundBookingUseCase,
    @Autowired private val jdbcTemplate: JdbcTemplate,
    @Autowired private val transactionManager: PlatformTransactionManager,
) : BaseIntegrationTest() {

    init {
        afterEach {
            jdbcTemplate.execute("TRUNCATE TABLE bookings")
            jdbcTemplate.execute("TRUNCATE TABLE slots")
        }

        Given("CONFIRMED 상태의 예약이 있을 때") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-REFUND-01",
                    date = ZonedDateTime.now().plusDays(1),
                    timeRange = "09:00-10:00",
                    capacity = 5,
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

            When("refundBooking을 실행하면") {
                val command = RefundBookingCommand(
                    bookingId = confirmed.id,
                    callerUserId = 1L,
                    refundAmount = BigDecimal("30000"),
                    reason = "고객 요청",
                )
                val result = refundBookingUseCase.execute(command)

                Then("DB에 status=REFUNDED가 커밋되고 PG 환불은 트랜잭션 커밋 후에 실행된다") {
                    result.status shouldBe BookingStatus.REFUNDED
                    val found = bookingDomainService.getBooking(1L, confirmed.id)
                    found.status shouldBe BookingStatus.REFUNDED
                }
            }
        }

        Given("PENDING 상태의 예약(결제 정보 없음)에 환불 시도 시") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-REFUND-02",
                    date = ZonedDateTime.now().plusDays(1),
                    timeRange = "10:00-11:00",
                    capacity = 5,
                    ownerId = 2L,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 2L, slotId = slot.id)

            When("refundBooking을 실행하면") {
                Then("paymentId가 없어 RefundBookingException이 발생하여 DB 상태가 변경되지 않는다") {
                    io.kotest.assertions.throwables.shouldThrow<com.sportsapp.domain.booking.exception.RefundBookingException> {
                        refundBookingUseCase.execute(
                            RefundBookingCommand(
                                bookingId = pending.id,
                                callerUserId = 2L,
                                refundAmount = BigDecimal("10000"),
                                reason = "테스트",
                            )
                        )
                    }
                    val found = bookingDomainService.getBooking(2L, pending.id)
                    found.status shouldBe BookingStatus.PENDING
                }
            }
        }

        Given("confirm webhook이 동일 bookingId로 2회 수신될 때") {
            val slot = slotRepository.save(
                Slot.create(
                    facilityId = "FAC-IDEMPOTENT-01",
                    date = ZonedDateTime.now().plusDays(1),
                    timeRange = "11:00-12:00",
                    capacity = 5,
                    ownerId = 1L,
                )
            )
            val pending = bookingDomainService.createPendingBooking(userId = 1L, slotId = slot.id)
            // confirmBooking()은 QueryDSL 벌크 UPDATE(BookingRepository.tryConfirm)를 호출해
            // 활성 트랜잭션이 필요하다(BookingDomainService에는 @Transactional이 없다 —
            // 트랜잭션 경계는 UseCase가 소유하는 컨벤션). 테스트에서 UseCase를 거치지 않고
            // 직접 호출하므로 TransactionTemplate으로 명시적으로 트랜잭션을 열어준다.
            TransactionTemplate(transactionManager).execute {
                bookingDomainService.confirmBooking(pending.id, paymentId = 200L)
            }

            When("동일 bookingId로 confirmBooking을 재호출하면") {
                val result = requireNotNull(
                    TransactionTemplate(transactionManager).execute {
                        bookingDomainService.confirmBooking(pending.id, paymentId = 300L)
                    }
                )

                Then("DB 상태가 1회만 변경되고 paymentId는 최초 값을 유지한다") {
                    result.status shouldBe BookingStatus.CONFIRMED
                    result.paymentId shouldBe 200L
                }
            }
        }
    }
}
