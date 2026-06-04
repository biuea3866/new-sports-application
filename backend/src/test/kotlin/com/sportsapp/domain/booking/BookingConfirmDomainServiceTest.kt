package com.sportsapp.domain.booking

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.math.BigDecimal
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.event.BookingConfirmedEvent
import com.sportsapp.domain.booking.event.BookingRefundRequestedEvent

class BookingConfirmDomainServiceTest : BehaviorSpec({

    val distributedLock = mockk<DistributedLock>(relaxed = true)

    Given("PENDING 상태의 Booking이 있을 때 confirmBooking 호출") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = BookingDomainService(bookingRepository, slotRepository, distributedLock, eventPublisher)

        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        every { bookingRepository.findById(1L) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }

        val capturedEvents = slot<List<DomainEvent>>()
        every { eventPublisher.publishAll(capture(capturedEvents)) } answers { Unit }

        When("confirmBooking을 호출하면") {
            service.confirmBooking(bookingId = 1L, paymentId = 999L)

            Then("BookingConfirmedEvent가 publishAll에 전달된다") {
                val events = capturedEvents.captured
                events.size shouldBe 1
                val confirmedEvent = events[0].shouldBeInstanceOf<BookingConfirmedEvent>()
                confirmedEvent.paymentId shouldBe 999L
            }
        }
    }

    Given("CONFIRMED 상태의 Booking에 confirmBooking 재호출 시") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = BookingDomainService(bookingRepository, slotRepository, distributedLock, eventPublisher)

        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 100L)
        booking.pullDomainEvents()
        every { bookingRepository.findById(2L) } returns booking

        When("confirmBooking을 재호출하면") {
            val result = service.confirmBooking(bookingId = 2L, paymentId = 200L)

            Then("멱등하게 처리되어 paymentId가 변경되지 않고 save와 publishAll이 호출되지 않는다") {
                result.status shouldBe BookingStatus.CONFIRMED
                result.paymentId shouldBe 100L
                verify(exactly = 0) { bookingRepository.save(any()) }
                verify(exactly = 0) { eventPublisher.publishAll(any()) }
            }
        }
    }

    Given("CONFIRMED 상태의 Booking에 refundBooking 호출 시") {
        val bookingRepository = mockk<BookingRepository>()
        val slotRepository = mockk<SlotRepository>()
        val eventPublisher = mockk<DomainEventPublisher>(relaxed = true)
        val service = BookingDomainService(bookingRepository, slotRepository, distributedLock, eventPublisher)

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
