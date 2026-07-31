package com.sportsapp.domain.booking.service

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * W1-11c — booking PENDING 예약 만료 스위퍼가 사용하는 [BookingDomainService]의
 * 후보 조회(findExpirableBookingIds)·만료 전이(expireBookings)를 검증한다.
 *
 * 슬롯 점유 해제는 별도 보상 로직이 아니라, PENDING → EXPIRED 전이 자체로 완료된다 —
 * 슬롯 점유는 countBySlotIdAndStatusIn(PENDING, CONFIRMED)로 파생되므로 EXPIRED로 전이되면
 * 그 즉시 활성 카운트에서 제외된다.
 */
class BookingExpiryDomainServiceTest : BehaviorSpec({

    fun buildService(bookingRepository: BookingRepository): BookingDomainService = BookingDomainService(
        bookingRepository = bookingRepository,
        slotRepository = mockk<SlotRepository>(),
        distributedLock = mockk<DistributedLock>(relaxed = true),
        domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true),
        bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>(),
    )

    Given("TTL 임계값과 조회 상한이 주어졌을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val threshold = ZonedDateTime.now().minusMinutes(15)
        every { bookingRepository.findPendingCreatedBefore(threshold, 100) } returns listOf(1L, 2L)

        When("findExpirableBookingIds를 호출하면") {
            val result = service.findExpirableBookingIds(threshold, 100)

            Then("BookingRepository 조회 결과를 그대로 반환한다") {
                result shouldBe listOf(1L, 2L)
            }
        }
    }

    Given("TTL이 지난 PENDING 예약이 있을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        every { bookingRepository.findById(1L) } returns booking
        every { bookingRepository.save(any()) } answers { firstArg() }

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(1L))

            Then("PENDING → EXPIRED로 전이되어 저장되고, 슬롯 점유(활성 카운트)에서 제외된다") {
                expiredCount shouldBe 1
                booking.status shouldBe BookingStatus.EXPIRED
                verify(exactly = 1) { bookingRepository.save(booking) }
            }
        }
    }

    Given("이미 EXPIRED 상태인 예약에") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.expire()
        every { bookingRepository.findById(2L) } returns booking

        When("expireBookings를 재호출하면") {
            val expiredCount = service.expireBookings(listOf(2L))

            Then("멱등하게 처리되어 save가 호출되지 않는다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { bookingRepository.save(any()) }
            }
        }
    }

    Given("이미 CONFIRMED 상태인 예약에") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.confirm(paymentId = 999L)
        every { bookingRepository.findById(3L) } returns booking

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(3L))

            Then("CONFIRMED는 만료 대상에서 제외되어 상태가 유지된다") {
                expiredCount shouldBe 0
                booking.status shouldBe BookingStatus.CONFIRMED
                verify(exactly = 0) { bookingRepository.save(any()) }
            }
        }
    }

    Given("사용자가 직접 취소한 CANCELLED 예약에") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)
        val booking = Booking.createPending(userId = 1L, slotId = 10L)
        booking.cancel()
        every { bookingRepository.findById(4L) } returns booking

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(listOf(4L))

            Then("취소와 만료가 구분되어 CANCELLED 상태가 유지된다") {
                expiredCount shouldBe 0
                booking.status shouldBe BookingStatus.CANCELLED
                verify(exactly = 0) { bookingRepository.save(any()) }
            }
        }
    }

    Given("만료 대상 id 목록이 비어있을 때") {
        val bookingRepository = mockk<BookingRepository>()
        val service = buildService(bookingRepository)

        When("expireBookings를 호출하면") {
            val expiredCount = service.expireBookings(emptyList())

            Then("조회·저장 없이 0을 반환한다") {
                expiredCount shouldBe 0
                verify(exactly = 0) { bookingRepository.findById(any()) }
                verify(exactly = 0) { bookingRepository.save(any()) }
            }
        }
    }
})
