package com.sportsapp.application.ticketing
import com.sportsapp.application.ticketing.usecase.GetTicketOrderUseCase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.dto.TicketOrderDetail
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.exception.UnauthorizedTicketOrderAccessException
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

class GetTicketOrderUseCaseTest : BehaviorSpec({

    Given("ticketOrderId=100이 DB에 존재하고 참조 Event도 존재할 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        val createdAt = ZonedDateTime.now()
        val detail = TicketOrderDetail(
            ticketOrderId = 100L,
            status = OrderStatus.PENDING,
            eventId = 7L,
            eventTitle = "월드컵 결승",
            paymentId = 555L,
            createdAt = createdAt,
        )
        every { ticketingDomainService.getTicketOrderDetail(100L, 1L) } returns detail

        When("requesterId=1로 execute(100, 1)를 호출하면") {
            val result = useCase.execute(100L, requesterId = 1L)

            Then("이벤트명·이벤트id·결제id·생성일시가 담긴 TicketOrderDetailResponse가 반환된다") {
                result.ticketOrderId shouldBe 100L
                result.status shouldBe OrderStatus.PENDING
                result.eventId shouldBe 7L
                result.eventTitle shouldBe "월드컵 결승"
                result.paymentId shouldBe 555L
                result.createdAt shouldBe createdAt
            }

            Then("도메인 서비스에 requesterId가 그대로 전달된다") {
                verify(exactly = 1) { ticketingDomainService.getTicketOrderDetail(100L, 1L) }
            }
        }
    }

    Given("ticketOrderId=200이 DB에 존재하나 결제 전(paymentId 없음)일 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        val createdAt = ZonedDateTime.now()
        val detail = TicketOrderDetail(
            ticketOrderId = 200L,
            status = OrderStatus.PENDING,
            eventId = 9L,
            eventTitle = "",
            paymentId = null,
            createdAt = createdAt,
        )
        every { ticketingDomainService.getTicketOrderDetail(200L, 1L) } returns detail

        When("requesterId=1로 execute(200, 1)를 호출하면") {
            val result = useCase.execute(200L, requesterId = 1L)

            Then("paymentId는 null, eventTitle은 빈 문자열로 방어된다") {
                result.paymentId shouldBe null
                result.eventTitle shouldBe ""
            }
        }
    }

    Given("ticketOrderId=999가 존재하지 않을 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        every {
            ticketingDomainService.getTicketOrderDetail(999L, 1L)
        } throws ResourceNotFoundException("TicketOrder", 999L)

        When("execute(999, 1)를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(999L, requesterId = 1L)
                }
            }
        }
    }

    Given("ticketOrderId=100이 존재하지만 requesterId=99가 소유자가 아닐 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        every {
            ticketingDomainService.getTicketOrderDetail(100L, 99L)
        } throws UnauthorizedTicketOrderAccessException(100L)

        When("execute(100, 99)를 호출하면") {
            Then("UnauthorizedTicketOrderAccessException이 발생한다") {
                shouldThrow<UnauthorizedTicketOrderAccessException> {
                    useCase.execute(100L, requesterId = 99L)
                }
            }
        }
    }
})
