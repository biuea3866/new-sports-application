package com.sportsapp.application.ticketing

import com.sportsapp.application.ticketing.usecase.CancelTicketingPaymentUseCase
import com.sportsapp.application.ticketing.usecase.ConfirmTicketingPaymentUseCase
import com.sportsapp.domain.ticketing.dto.TicketOrderResult
import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class TicketingPaymentUseCaseTest : BehaviorSpec({

    Given("확정 UseCase") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = ConfirmTicketingPaymentUseCase(ticketingDomainService)
        every { ticketingDomainService.confirmOrder(30L, 300L) } returns mockk<TicketOrderResult>()

        When("execute(orderId, paymentId) 를 호출하면") {
            useCase.execute(30L, 300L)

            Then("TicketingDomainService.confirmOrder 에 위임한다") {
                verify(exactly = 1) { ticketingDomainService.confirmOrder(30L, 300L) }
            }
        }
    }

    Given("취소 UseCase") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = CancelTicketingPaymentUseCase(ticketingDomainService)
        justRun { ticketingDomainService.cancelOrder(any()) }

        When("execute(orderId) 를 호출하면") {
            useCase.execute(31L)

            Then("TicketingDomainService.cancelOrder 에 위임한다") {
                verify(exactly = 1) { ticketingDomainService.cancelOrder(31L) }
            }
        }
    }
})
