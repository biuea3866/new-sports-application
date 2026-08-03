package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.event.BookingEvent
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEvent
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.FeatureFlagEvaluator
import com.sportsapp.domain.booking.service.BookingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot

/**
 * 예약 확정 이벤트에 **시설 소유주**를 함께 싣는다.
 *
 * 알림 컨텍스트는 예약이 어느 시설 것인지 모른다. 소유주를 알아내려고 notification이 booking을
 * 역참조하면 공용 컨텍스트가 주문 컨텍스트를 참조하게 되므로, 소유주를 아는 booking이 자기
 * 이벤트 payload에 담아 발행한다(이벤트 계약 확장 > 역참조).
 */
class BookingConfirmOwnerEventTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()
    val featureFlagEvaluator = mockk<FeatureFlagEvaluator>()

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
        bookingOrderQueryRepository,
        featureFlagEvaluator,
    )

    val bookingId = 1L
    val paymentId = 500L
    val bookerUserId = 68L
    val facilityOwnerUserId = 69L
    val targetSlotId = 2L

    Given("내 시설 슬롯에 걸린 예약이 결제 확정될 때") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns bookingId
            every { slotId } returns targetSlotId
            every { userId } returns bookerUserId
            every { status } returns BookingStatus.CONFIRMED
        }
        every { bookingRepository.tryConfirm(bookingId = bookingId, paymentId = paymentId) } returns true
        every { bookingRepository.findById(bookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns mockk<Slot>(relaxed = true) {
            every { ownerId } returns facilityOwnerUserId
        }
        val publishedSlot = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(publishedSlot)) } returns Unit

        When("예약을 확정하면") {
            service.confirmBooking(bookingId, paymentId)

            Then("확정 이벤트에 예약자와 시설 소유주가 모두 실린다") {
                val confirmed = publishedSlot.captured.first() as BookingEvent.Confirmed
                confirmed.recipientUserId shouldBe bookerUserId
                confirmed.facilityOwnerUserId shouldBe facilityOwnerUserId
            }
        }
    }

    Given("참조 슬롯을 찾을 수 없는 예약이 확정될 때") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns bookingId
            every { slotId } returns targetSlotId
            every { userId } returns bookerUserId
            every { status } returns BookingStatus.CONFIRMED
        }
        every { bookingRepository.tryConfirm(bookingId = bookingId, paymentId = paymentId) } returns true
        every { bookingRepository.findById(bookingId) } returns booking
        every { slotRepository.findById(targetSlotId) } returns null
        val publishedSlot = slot<List<DomainEvent>>()
        every { domainEventPublisher.publishAll(capture(publishedSlot)) } returns Unit

        When("예약을 확정하면") {
            service.confirmBooking(bookingId, paymentId)

            // 소유주를 못 찾아도 구매자 확정 알림까지 막으면 안 된다 — 소유주만 비운다.
            Then("소유주 없이도 확정 이벤트는 발행된다") {
                val confirmed = publishedSlot.captured.first() as BookingEvent.Confirmed
                confirmed.recipientUserId shouldBe bookerUserId
                confirmed.facilityOwnerUserId shouldBe null
            }
        }
    }
})
