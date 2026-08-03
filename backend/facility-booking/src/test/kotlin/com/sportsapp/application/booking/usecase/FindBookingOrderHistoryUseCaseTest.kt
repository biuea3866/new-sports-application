package com.sportsapp.application.booking.usecase

import com.sportsapp.domain.booking.dto.BookingOrderItem
import com.sportsapp.domain.booking.entity.BookingStatus
import com.sportsapp.domain.booking.service.BookingDomainService
import com.sportsapp.domain.facility.repository.FacilityRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class FindBookingOrderHistoryUseCaseTest : BehaviorSpec({

    fun orderItem(bookingId: Long, userId: Long, status: BookingStatus = BookingStatus.CONFIRMED) = BookingOrderItem(
        bookingId = bookingId,
        slotId = 10L,
        userId = userId,
        status = status,
        paymentId = 100L,
        title = "8월 3일 10:00~11:00",
        createdAt = ZonedDateTime.now(),
    )

    Given("사용자의 예약 이력을 조회하면") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(1L) } returns listOf(orderItem(bookingId = 1L, userId = 1L))
        val useCase = FindBookingOrderHistoryUseCase(bookingDomainService)

        When("execute(1L)를 호출하면") {
            val result = useCase.execute(1L)

            Then("계약 필드(bookingId·title·status·paymentId·createdAt)만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result.first().bookingId shouldBe 1L
                result.first().status shouldBe "CONFIRMED"
                result.first().paymentId shouldBe 100L
            }
        }
    }

    Given("다른 사용자의 예약이 섞여 있는 상황에서") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(1L) } returns listOf(orderItem(bookingId = 1L, userId = 1L))
        every { bookingDomainService.findOrderHistory(2L) } returns listOf(orderItem(bookingId = 2L, userId = 2L))
        val useCase = FindBookingOrderHistoryUseCase(bookingDomainService)

        When("execute(1L)를 호출하면") {
            val result = useCase.execute(1L)

            Then("요청한 사용자(1L)의 예약만 조회하고 다른 사용자(2L)의 예약은 포함되지 않는다") {
                result.map { it.bookingId } shouldBe listOf(1L)
                verify(exactly = 1) { bookingDomainService.findOrderHistory(1L) }
                verify(exactly = 0) { bookingDomainService.findOrderHistory(2L) }
            }
        }
    }

    Given("조회 결과가 0건이면") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(1L) } returns emptyList()
        val useCase = FindBookingOrderHistoryUseCase(bookingDomainService)

        When("execute(1L)를 호출하면") {
            val result = useCase.execute(1L)

            Then("빈 목록을 반환한다") {
                result shouldBe emptyList()
            }
        }
    }

    Given("이 UseCase의 의존 구성을") {
        Then("MongoDB 소유 저장소(FacilityRepository)를 의존하지 않는다") {
            val constructorParameterTypes = FindBookingOrderHistoryUseCase::class.java.declaredConstructors
                .single()
                .parameterTypes
            constructorParameterTypes.contains(FacilityRepository::class.java) shouldBe false
        }
    }
})
