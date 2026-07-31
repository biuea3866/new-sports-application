package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * [ExpireTicketOrderChunkUseCase] — W1-11b 만료 스위퍼의 청크 단위 트랜잭션 경계.
 * `@Transactional`을 UseCase로 옮겨 컨벤션(트랜잭션은 UseCase에 선언)을 지키면서도,
 * 별도 빈이라 [ExpirePendingTicketOrdersUseCase]의 청크 루프에서 self-invocation 없이
 * 호출된다.
 */
class ExpireTicketOrderChunkUseCaseTest : BehaviorSpec({

    Given("만료 대상 id 목록이 주어졌을 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = ExpireTicketOrderChunkUseCase(ticketingDomainService)
        every { ticketingDomainService.expireTicketOrders(listOf(1L, 2L)) } returns 2

        When("execute를 호출하면") {
            val result = useCase.execute(listOf(1L, 2L))

            Then("TicketingDomainService.expireTicketOrders에 위임하고 결과를 그대로 반환한다") {
                result shouldBe 2
                verify(exactly = 1) { ticketingDomainService.expireTicketOrders(listOf(1L, 2L)) }
            }
        }
    }

    Given("빈 목록이 주어졌을 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = ExpireTicketOrderChunkUseCase(ticketingDomainService)
        every { ticketingDomainService.expireTicketOrders(emptyList()) } returns 0

        When("execute를 호출하면") {
            val result = useCase.execute(emptyList())

            Then("0을 반환한다") {
                result shouldBe 0
            }
        }
    }
})
