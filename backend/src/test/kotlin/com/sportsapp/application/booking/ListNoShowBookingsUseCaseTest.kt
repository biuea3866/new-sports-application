package com.sportsapp.application.booking

import com.sportsapp.domain.booking.Booking
import com.sportsapp.domain.booking.BookingDomainService
import com.sportsapp.domain.booking.BookingStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.ZonedDateTime

class ListNoShowBookingsUseCaseTest : BehaviorSpec({

    val bookingDomainService = mockk<BookingDomainService>()
    val useCase = ListNoShowBookingsUseCase(bookingDomainService)

    Given("[U-01] 노쇼 예약 2건이 존재하는 운영자") {
        val pageable = PageRequest.of(0, 20)
        val now = ZonedDateTime.now()
        val from = now.minusDays(7)
        val to = now.minusDays(1)

        val booking1 = mockk<Booking>(relaxed = true) {
            every { id } returns 1L
            every { userId } returns 10L
            every { slotId } returns 100L
            every { status } returns BookingStatus.NO_SHOW
            every { createdAt } returns from.plusDays(1)
        }
        val booking2 = mockk<Booking>(relaxed = true) {
            every { id } returns 2L
            every { userId } returns 11L
            every { slotId } returns 101L
            every { status } returns BookingStatus.NO_SHOW
            every { createdAt } returns from.plusDays(2)
        }
        every {
            bookingDomainService.listNoShows(
                operatorUserId = 1L,
                facilityId = null,
                from = from,
                to = to,
                pageable = pageable,
            )
        } returns PageImpl(listOf(booking1, booking2), pageable, 2L)

        When("[U-01] UseCase 실행 시") {
            val command = ListNoShowBookingsCommand(
                operatorUserId = 1L,
                facilityId = null,
                from = from,
                to = to,
                pageable = pageable,
            )
            val response = useCase.execute(command)

            Then("[U-01] DomainService 모킹 후 정상 노쇼 booking 목록이 반환된다") {
                response.bookings.size shouldBe 2
                response.totalElements shouldBe 2L
                response.bookings[0].status shouldBe BookingStatus.NO_SHOW
            }
        }
    }

    Given("[U-02] from이 미래인 커맨드") {
        val pageable = PageRequest.of(0, 20)
        val from = ZonedDateTime.now().plusDays(1)
        val to = ZonedDateTime.now().plusDays(7)

        When("[U-02] UseCase 실행 시") {
            val command = ListNoShowBookingsCommand(
                operatorUserId = 1L,
                facilityId = null,
                from = from,
                to = to,
                pageable = pageable,
            )

            Then("[U-02] 미래 날짜 범위 입력 시 IllegalArgumentException이 발생한다") {
                shouldThrow<IllegalArgumentException> {
                    useCase.execute(command)
                }
            }
        }
    }

    Given("[U-06] UseCase 내부에 Repository 직접 의존이 없는지 확인") {
        Then("[U-06] 생성자에 DomainService만 있다") {
            val constructors = ListNoShowBookingsUseCase::class.constructors
            constructors.size shouldBe 1
            constructors.first().parameters.size shouldBe 1
        }
    }
})
