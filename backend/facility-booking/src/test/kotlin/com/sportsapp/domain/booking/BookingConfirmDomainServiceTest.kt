package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.exception.InvalidBookingStateException
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureFlagEvaluator
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.booking.event.BookingRefundRequestedEvent

/**
 * confirmBooking은 [BookingRepository.tryConfirm] CAS(조건부 UPDATE, WHERE status='PENDING')로
 * 전이한다 — 비잠금 findById → confirm() → save() 경로는 청크 스위퍼가 커밋한 EXPIRED를
 * 조건 없는 dirty-checking UPDATE로 덮어쓰는 반대 방향 lost update(오버부킹)를 만들 수 있어
 * tryExpire와 대칭으로 닫았다(p2-3).
 */
class BookingConfirmDomainServiceTest : BehaviorSpec({

    val distributedLock = mockk<DistributedLock>(relaxed = true)

    Given("PENDING 상태의 Booking을 confirmBooking으로 확정할 때") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
        val service = BookingDomainService(
            bookingRepository,
            slotRepository,
            distributedLock,
            eventPublisher,
            bookingOrderQueryRepository,
            mockk<FeatureFlagEvaluator>(),
        )

        // CAS 성공 후 재조회하면 CONFIRMED로 반영된 최신 행이 보인다(같은 트랜잭션·커넥션 내 가시성).
        val confirmedBooking = Booking.createPending(userId = 1L, slotId = 10L)
        confirmedBooking.confirm(paymentId = 999L)
        confirmedBooking.pullDomainEvents()
        every { bookingRepository.tryConfirm(1L, 999L) } returns true
        every { bookingRepository.findById(1L) } returns confirmedBooking
        // 확정 이벤트에 시설 소유주를 담기 위해 슬롯을 조회한다(알림 컨텍스트의 booking 역참조 회피).
        every { slotRepository.findById(10L) } returns mockk<Slot>(relaxed = true) {
            every { ownerId } returns 69L
        }

        val capturedEvents = slot<List<DomainEvent>>()
        every { eventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("confirmBooking을 호출하면") {
            val result = service.confirmBooking(bookingId = 1L, paymentId = 999L)

            Then("CAS 전이가 성공해 BookingEvent.Confirmed가 publishAll에 전달된다") {
                result.status shouldBe BookingStatus.CONFIRMED
                val events = capturedEvents.captured
                events.size shouldBe 1
                val confirmedEvent = events[0].shouldBeInstanceOf<BookingEvent.Confirmed>()
                confirmedEvent.paymentId shouldBe 999L
                confirmedEvent.recipientUserId shouldBe 1L
                confirmedEvent.facilityOwnerUserId shouldBe 69L
            }
        }
    }

    Given("이미 CONFIRMED 상태인 Booking에 confirmBooking을 재호출할 때 (webhook 중복 — 멱등)") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
        val service = BookingDomainService(
            bookingRepository,
            slotRepository,
            distributedLock,
            eventPublisher,
            bookingOrderQueryRepository,
            mockk<FeatureFlagEvaluator>(),
        )

        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 100L)
        booking.pullDomainEvents()
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 CONFIRMED).
        every { bookingRepository.tryConfirm(2L, 200L) } returns false
        every { bookingRepository.findById(2L) } returns booking

        When("confirmBooking을 재호출하면") {
            val result = service.confirmBooking(bookingId = 2L, paymentId = 200L)

            Then("멱등하게 처리되어 기존 paymentId가 유지되고 이벤트가 재발행되지 않는다") {
                result.status shouldBe BookingStatus.CONFIRMED
                result.paymentId shouldBe 100L
                verify(exactly = 0) { eventPublisher.publishAll(any()) }
            }
        }
    }

    Given("만료 스위퍼가 먼저 EXPIRED로 전이시킨 뒤 confirmBooking이 뒤늦게 도착할 때 (핵심 회귀 — 반대 방향 lost update 방지)") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
        val service = BookingDomainService(
            bookingRepository,
            slotRepository,
            distributedLock,
            eventPublisher,
            bookingOrderQueryRepository,
            mockk<FeatureFlagEvaluator>(),
        )

        val expiredBooking = Booking.createPending(userId = 1L, slotId = 10L)
        expiredBooking.expire()
        // CAS는 WHERE status='PENDING' 조건에 걸려 실패한다(이미 EXPIRED로 전이됨).
        every { bookingRepository.tryConfirm(4L, 300L) } returns false
        every { bookingRepository.findById(4L) } returns expiredBooking

        When("confirmBooking을 호출하면") {
            Then("InvalidBookingStateException을 던져 CONFIRMED로 덮어쓰지 않는다 (오버부킹 방지)") {
                val exception = shouldThrow<InvalidBookingStateException> {
                    service.confirmBooking(bookingId = 4L, paymentId = 300L)
                }
                exception.message shouldContain "EXPIRED"
                verify(exactly = 0) { eventPublisher.publishAll(any()) }
            }
        }
    }

    Given("CONFIRMED 상태의 Booking에 refundBooking 호출 시") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
        val service = BookingDomainService(
            bookingRepository,
            slotRepository,
            distributedLock,
            eventPublisher,
            bookingOrderQueryRepository,
            mockk<FeatureFlagEvaluator>(),
        )

        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 50L)
        booking.pullDomainEvents()
        every { bookingRepository.findById(3L) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }

        val capturedEvents = slot<List<DomainEvent>>()
        every { eventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("refundBooking을 호출하면") {
            service.refundBooking(
                bookingId = 3L,
                callerUserId = 1L,
                refundAmount = BigDecimal("10000"),
                reason = "테스트",
            )

            Then("booking.refund()와 save()가 먼저 호출된 뒤 BookingRefundRequestedEvent가 publishAll에 전달된다") {
                booking.status shouldBe BookingStatus.REFUNDED
                verify(exactly = 1) { bookingRepository.save(any()) }
                val refundEvents = capturedEvents.captured.filterIsInstance<BookingRefundRequestedEvent>()
                refundEvents.size shouldBe 1
                refundEvents[0].paymentId shouldBe 50L
                refundEvents[0].refundAmount shouldBe BigDecimal("10000")
                refundEvents[0].reason shouldBe "테스트"
            }
        }
    }
})
