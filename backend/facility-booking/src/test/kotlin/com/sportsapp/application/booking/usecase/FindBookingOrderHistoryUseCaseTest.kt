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
import java.math.BigDecimal
import java.time.ZonedDateTime

class FindBookingOrderHistoryUseCaseTest : BehaviorSpec({

    fun orderItem(
        bookingId: Long,
        userId: Long,
        status: BookingStatus = BookingStatus.CONFIRMED,
        amount: BigDecimal? = BigDecimal("30000"),
    ) = BookingOrderItem(
        bookingId = bookingId,
        slotId = 10L,
        userId = userId,
        status = status,
        paymentId = 100L,
        title = "8월 3일 10:00~11:00",
        createdAt = ZonedDateTime.now(),
        amount = amount,
    )

    Given("사용자의 예약 이력을 조회하면") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(1L) } returns listOf(orderItem(bookingId = 1L, userId = 1L))
        val useCase = FindBookingOrderHistoryUseCase(bookingDomainService)

        When("execute(1L)를 호출하면") {
            val result = useCase.execute(1L)

            Then("계약 필드(sourceId·title·status·paymentId·createdAt)만 담은 응답을 반환한다") {
                result.size shouldBe 1
                result.first().sourceId shouldBe 1L
                result.first().status shouldBe "CONFIRMED"
                result.first().paymentId shouldBe 100L
            }

            Then("예약 자기 데이터인 결제 금액을 함께 공급한다 — edge 가 payment 를 역참조하지 않는 근거다") {
                result.first().amount shouldBe BigDecimal("30000")
            }
        }
    }

    Given("금액 저장 이력이 없는(V65 이전) 예약을 조회하면") {
        val bookingDomainService = mockk<BookingDomainService>()
        every { bookingDomainService.findOrderHistory(1L) } returns
            listOf(orderItem(bookingId = 1L, userId = 1L, amount = null))
        val useCase = FindBookingOrderHistoryUseCase(bookingDomainService)

        When("execute(1L)를 호출하면") {
            val result = useCase.execute(1L)

            Then("금액을 null 로 공급한다 (0원 확정값과 구분한다)") {
                result.first().amount shouldBe null
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
                result.map { it.sourceId } shouldBe listOf(1L)
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
