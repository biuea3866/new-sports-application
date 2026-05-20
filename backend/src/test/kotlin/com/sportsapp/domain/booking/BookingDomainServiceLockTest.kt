package com.sportsapp.domain.booking

import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration

class BookingDomainServiceLockTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
    )

    Given("[U-01] tryLock이 false를 반환하는 상황") {
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns false

        When("requestBooking을 호출하면") {
            Then("[U-01] SlotBusyException을 던지고 Booking을 생성하지 않는다") {
                shouldThrow<SlotBusyException> {
                    service.requestBooking(userId = 1L, slotId = 42L)
                }
                verify(exactly = 0) { bookingRepository.save(any()) }
            }
        }
    }

    Given("[U-02] 락 획득 성공 + slot.capacity 잔여 0건인 상황") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = java.time.ZonedDateTime.now(),
            timeRange = "09:00-10:00",
            capacity = 1,
        )
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { slotRepository.findById(42L) } returns slot
        every { bookingRepository.countBySlotIdAndStatusIn(42L, any()) } returns 1L

        When("requestBooking을 호출하면") {
            Then("[U-02] SlotFullException을 던진다") {
                shouldThrow<SlotFullException> {
                    service.requestBooking(userId = 1L, slotId = 42L)
                }
            }
        }
    }

    Given("[U-03] 락 획득 성공 + slot 조회 실패 (중간 예외 발생)") {
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { slotRepository.findById(999L) } returns null

        When("requestBooking을 호출하면") {
            Then("[U-03] 예외 발생 시에도 unlock이 finally에서 호출된다") {
                shouldThrow<ResourceNotFoundException> {
                    service.requestBooking(userId = 1L, slotId = 999L)
                }
                verify(exactly = 1) { distributedLock.unlock("booking:slot:999", "user:1") }
            }
        }
    }

    Given("[U-04] 정상 흐름") {
        val slot = Slot.create(
            facilityId = "FAC-01",
            date = java.time.ZonedDateTime.now(),
            timeRange = "10:00-11:00",
            capacity = 5,
        )
        val booking = Booking.createPending(userId = 1L, slotId = 42L)
        every { distributedLock.tryLock(any(), any(), any<Duration>()) } returns true
        every { distributedLock.unlock(any(), any()) } returns true
        every { slotRepository.findById(42L) } returns slot
        every { bookingRepository.countBySlotIdAndStatusIn(42L, any()) } returns 2L
        every { bookingRepository.save(any()) } returns booking

        When("requestBooking을 호출하면") {
            val result = service.requestBooking(userId = 1L, slotId = 42L)

            Then("PENDING 상태의 Booking이 반환된다") {
                result.status shouldBe BookingStatus.PENDING
                result.userId shouldBe 1L
                result.slotId shouldBe 42L
            }
        }
    }
})
