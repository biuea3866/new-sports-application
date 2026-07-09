package com.sportsapp.domain.booking

import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.entity.Slot
import com.sportsapp.domain.booking.exception.UnauthorizedBookingAccessException
import com.sportsapp.domain.booking.repository.BookingOrderQueryRepository
import com.sportsapp.domain.booking.repository.BookingRepository
import com.sportsapp.domain.booking.repository.SlotRepository
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.common.DistributedLock
import com.sportsapp.domain.common.DomainEventPublisher
import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZoneOffset
import java.time.ZonedDateTime

class BookingDomainServiceGetBookingDetailTest : BehaviorSpec({

    val bookingRepository = mockk<BookingRepository>()
    val slotRepository = mockk<SlotRepository>()
    val distributedLock = mockk<DistributedLock>()
    val domainEventPublisher = mockk<DomainEventPublisher>(relaxed = true)
    val bookingOrderQueryRepository = mockk<BookingOrderQueryRepository>()

    val service = BookingDomainService(
        bookingRepository,
        slotRepository,
        distributedLock,
        domainEventPublisher,
        bookingOrderQueryRepository,
    )

    Given("본인 소유 Booking과 해당 Slot이 모두 존재하는 상황") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 10L
            every { slotId } returns 42L
            every { userId } returns 1L
            every { status } returns BookingStatus.CONFIRMED
            every { paymentId } returns 50L
            every { createdAt } returns ZonedDateTime.now()
            every { updatedAt } returns ZonedDateTime.now()
        }
        val slotDate = ZonedDateTime.of(2026, 7, 10, 0, 0, 0, 0, ZoneOffset.UTC)
        val slot = mockk<Slot>(relaxed = true) {
            every { facilityId } returns "FAC-01"
            every { date } returns slotDate
            every { timeRange } returns "09:00-10:00"
        }
        every { bookingRepository.findById(10L) } returns booking
        every { slotRepository.findById(42L) } returns slot

        When("getBookingDetail을 호출하면") {
            val detail = service.getBookingDetail(requesterId = 1L, bookingId = 10L)

            Then("Slot의 facilityId·title이 채워진 BookingDetail이 반환된다") {
                detail.bookingId shouldBe 10L
                detail.facilityId shouldBe "FAC-01"
                detail.title shouldBe "2026-07-10 09:00-10:00 시설 예약"
            }
        }
    }

    Given("Booking은 존재하나 참조 Slot이 삭제·부재인 상황") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 11L
            every { slotId } returns 999L
            every { userId } returns 1L
            every { status } returns BookingStatus.CONFIRMED
            every { paymentId } returns null
            every { createdAt } returns ZonedDateTime.now()
            every { updatedAt } returns ZonedDateTime.now()
        }
        every { bookingRepository.findById(11L) } returns booking
        every { slotRepository.findById(999L) } returns null

        When("getBookingDetail을 호출하면") {
            val detail = service.getBookingDetail(requesterId = 1L, bookingId = 11L)

            Then("facilityId는 null, title은 기본 라벨로 방어 반환된다") {
                detail.facilityId shouldBe null
                detail.title shouldBe "시설 예약"
            }
        }
    }

    Given("타인이 Booking 단건 상세를 조회하는 상황") {
        val booking = mockk<Booking>(relaxed = true) {
            every { id } returns 12L
            every { slotId } returns 42L
            every { userId } returns 1L
        }
        every { bookingRepository.findById(12L) } returns booking

        When("getBookingDetail을 호출하면") {
            Then("UnauthorizedBookingAccessException이 발생한다") {
                shouldThrow<UnauthorizedBookingAccessException> {
                    service.getBookingDetail(requesterId = 2L, bookingId = 12L)
                }
            }
        }
    }

    Given("존재하지 않는 Booking을 조회하는 상황") {
        every { bookingRepository.findById(404L) } returns null

        When("getBookingDetail을 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    service.getBookingDetail(requesterId = 1L, bookingId = 404L)
                }
            }
        }
    }
})
