package com.sportsapp.application.booking

import com.sportsapp.application.booking.dto.ListFacilityOwnerBookingsCommand
import com.sportsapp.application.booking.usecase.ListFacilityOwnerBookingsUseCase
import com.sportsapp.domain.booking.entity.Booking
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.payment.entity.PaymentStatus
import com.sportsapp.domain.payment.service.PaymentDomainService
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest

/**
 * 포털 "예약 관리" — 파트너가 소유한 시설의 예약을 조회한다.
 *
 * 예약자 스코프(`findMyBookings`)를 쓰면 파트너 본인이 예약한 건만 나와 소유 시설에 예약이
 * 실재해도 0건이 된다. 소유자 스코프 조회를 쓰는지, 그 인자로 인증된 파트너 id가 넘어가는지
 * 검증한다.
 */
class ListFacilityOwnerBookingsUseCaseTest : io.kotest.core.spec.style.BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val paymentDomainService = mockk<PaymentDomainService>()
    val useCase = ListFacilityOwnerBookingsUseCase(bookingDomainService, paymentDomainService)

    val partnerOwnerId = 69L
    val pageable = PageRequest.of(0, 20)

    fun buildBooking(id: Long, bookerUserId: Long, paymentId: Long?): Booking {
        val booking = mockk<Booking>()
        every { booking.id } returns id
        every { booking.slotId } returns 2L
        every { booking.userId } returns bookerUserId
        every { booking.status } returns BookingStatus.CONFIRMED
        every { booking.paymentId } returns paymentId
        every { booking.createdAt } returns java.time.ZonedDateTime.now()
        every { booking.updatedAt } returns java.time.ZonedDateTime.now()
        return booking
    }

    Given("내 시설에 다른 사람이 건 예약이 있을 때") {
        val command = ListFacilityOwnerBookingsCommand(
            ownerUserId = partnerOwnerId,
            status = null,
            pageable = pageable,
        )
        val booking = buildBooking(id = 1L, bookerUserId = 68L, paymentId = 500L)
        every {
            bookingDomainService.findBookingsForFacilityOwner(partnerOwnerId, null, pageable)
        } returns PageImpl(listOf(booking), pageable, 1L)
        every { paymentDomainService.findStatuses(listOf(500L)) } returns mapOf(500L to PaymentStatus.COMPLETED)

        When("예약 목록을 조회하면") {
            val result = useCase.execute(command)

            Then("예약자가 내가 아니어도 내 시설 예약이 반환된다") {
                result.totalElements shouldBe 1L
                result.bookings.first().id shouldBe 1L
                result.bookings.first().userId shouldBe 68L
            }

            Then("결제 상태가 함께 실린다") {
                result.bookings.first().paymentStatus shouldBe PaymentStatus.COMPLETED
            }

            // 예약자 스코프 조회를 쓰면 파트너 본인 예약만 나와 0건이 된다 — 회귀 방지.
            Then("예약자 스코프 조회를 사용하지 않는다") {
                verify(exactly = 0) { bookingDomainService.findMyBookings(any(), any(), any()) }
            }
        }
    }

    Given("상태 필터를 지정했을 때") {
        val command = ListFacilityOwnerBookingsCommand(
            ownerUserId = partnerOwnerId,
            status = BookingStatus.CONFIRMED,
            pageable = pageable,
        )
        every {
            bookingDomainService.findBookingsForFacilityOwner(partnerOwnerId, BookingStatus.CONFIRMED, pageable)
        } returns PageImpl(emptyList(), pageable, 0L)
        every { paymentDomainService.findStatuses(emptyList()) } returns emptyMap()

        When("예약 목록을 조회하면") {
            useCase.execute(command)

            Then("인증된 파트너 id와 상태가 그대로 도메인 서비스에 전달된다") {
                verify(exactly = 1) {
                    bookingDomainService.findBookingsForFacilityOwner(
                        partnerOwnerId,
                        BookingStatus.CONFIRMED,
                        pageable,
                    )
                }
            }
        }
    }

    Given("내 시설에 예약이 없을 때") {
        val command = ListFacilityOwnerBookingsCommand(
            ownerUserId = partnerOwnerId,
            status = null,
            pageable = pageable,
        )
        every {
            bookingDomainService.findBookingsForFacilityOwner(partnerOwnerId, null, pageable)
        } returns PageImpl(emptyList(), pageable, 0L)
        every { paymentDomainService.findStatuses(emptyList()) } returns emptyMap()

        When("예약 목록을 조회하면") {
            val result = useCase.execute(command)

            Then("빈 목록이 반환된다") {
                result.totalElements shouldBe 0L
                result.bookings.shouldBeEmpty()
            }
        }
    }

    Given("결제가 아직 없는 예약만 있을 때") {
        val command = ListFacilityOwnerBookingsCommand(
            ownerUserId = partnerOwnerId,
            status = null,
            pageable = pageable,
        )
        val unpaidBooking = buildBooking(id = 2L, bookerUserId = 68L, paymentId = null)
        every {
            bookingDomainService.findBookingsForFacilityOwner(partnerOwnerId, null, pageable)
        } returns PageImpl(listOf(unpaidBooking), pageable, 1L)
        every { paymentDomainService.findStatuses(emptyList()) } returns emptyMap()

        When("예약 목록을 조회하면") {
            val result = useCase.execute(command)

            Then("결제 상태 없이 예약이 반환된다") {
                result.bookings.first().paymentId shouldBe null
                result.bookings.first().paymentStatus shouldBe null
            }
        }
    }
})
