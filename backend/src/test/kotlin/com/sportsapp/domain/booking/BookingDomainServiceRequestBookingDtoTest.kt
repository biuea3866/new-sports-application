package com.sportsapp.domain.booking

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.Duration

class BookingDomainServiceRequestBookingDtoTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val paymentRefundGateway = mockk<PaymentRefundGateway>(relaxed = true)

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
        paymentRefundGateway,
    )

    Given("슬롯 잔여가 충분하고 락 획득에 성공하는 상황") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = java.time.ZonedDateTime.now(),
            timeRange = "10:00-11:00",
            capacity = 5,
            ownerId = 1L,
        )
        val booking = Booking.createPending(userId = 1L, slotId = 42L)
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { slotRepository.findById(42L) } returns slot
        every { bookingRepository.countBySlotIdAndStatusIn(42L, any()) } returns 0L
        every { bookingRepository.save(any()) } returns booking

        When("requestBooking 을 호출하면") {
            val result = service.requestBooking(userId = 1L, slotId = 42L)

            Then("BookingResult 가 반환되고 status 가 PENDING 이다") {
                result.status shouldBe BookingStatus.PENDING
                result.userId shouldBe 1L
                result.slotId shouldBe 42L
                result.bookingId shouldBe 0L
            }
        }
    }

    Given("슬롯이 꽉 찬 상황") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = java.time.ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 1,
            ownerId = 1L,
        )
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { slotRepository.findById(42L) } returns slot
        every { bookingRepository.countBySlotIdAndStatusIn(42L, any()) } returns 1L

        When("requestBooking 을 호출하면") {
            Then("SlotFullException 이 발생한다") {
                io.kotest.assertions.throwables.shouldThrow<SlotFullException> {
                    service.requestBooking(userId = 1L, slotId = 42L)
                }
            }
        }
    }
})
