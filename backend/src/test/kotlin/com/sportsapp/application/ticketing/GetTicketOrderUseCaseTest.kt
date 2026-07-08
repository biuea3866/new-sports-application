package com.sportsapp.application.ticketing
import com.sportsapp.application.ticketing.usecase.GetTicketOrderUseCase

import com.sportsapp.domain.common.exceptions.ResourceNotFoundException
import com.sportsapp.domain.ticketing.entity.OrderStatus
import com.sportsapp.domain.ticketing.entity.TicketOrder
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetTicketOrderUseCaseTest : BehaviorSpec({

    Given("ticketOrderId=100이 DB에 존재할 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        val order = mockk<TicketOrder>(relaxed = true).also {
            every { it.id } returns 100L
            every { it.status } returns OrderStatus.PENDING
        }
        every { ticketingDomainService.getTicketOrder(100L) } returns order

        When("execute(100)를 호출하면") {
            val result = useCase.execute(100L)

            Then("ticketOrderId와 status가 담긴 TicketOrderResponse가 반환된다") {
                result.ticketOrderId shouldBe 100L
                result.status shouldBe OrderStatus.PENDING
            }
        }
    }

    Given("ticketOrderId=999가 존재하지 않을 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = GetTicketOrderUseCase(ticketingDomainService)
        every { ticketingDomainService.getTicketOrder(999L) } throws ResourceNotFoundException("TicketOrder", 999L)

        When("execute(999)를 호출하면") {
            Then("ResourceNotFoundException이 발생한다") {
                shouldThrow<ResourceNotFoundException> {
                    useCase.execute(999L)
                }
            }
        }
    }
})
