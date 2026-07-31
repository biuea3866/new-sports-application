package com.sportsapp.application.ticketing.usecase

import com.sportsapp.domain.ticketing.service.TicketingDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

/**
 * [IsTicketOrderExpiryEnabledUseCase] — ticketing.expiry.enabled 킬 스위치 판정을
 * [TicketingDomainService.isExpiryEnabled]에 위임한다 (부팅 고정 properties가 아니라
 * FeatureFlagEvaluator 런타임 조회).
 */
class IsTicketOrderExpiryEnabledUseCaseTest : BehaviorSpec({

    Given("플래그가 활성화되어 있을 때") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = IsTicketOrderExpiryEnabledUseCase(ticketingDomainService)
        every { ticketingDomainService.isExpiryEnabled() } returns true

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("true를 반환한다") {
                result shouldBe true
            }
        }
    }

    Given("플래그가 비활성화되어 있을 때(운영 킬 스위치)") {
        val ticketingDomainService = mockk<TicketingDomainService>()
        val useCase = IsTicketOrderExpiryEnabledUseCase(ticketingDomainService)
        every { ticketingDomainService.isExpiryEnabled() } returns false

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }
})
