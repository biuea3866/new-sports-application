package com.sportsapp.application.message.usecase

import com.sportsapp.domain.message.service.GuestEvictionDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ExpireGuestsUseCaseTest : BehaviorSpec({

    val guestEvictionDomainService = mockk<GuestEvictionDomainService>()
    val expireGuestsUseCase = ExpireGuestsUseCase(guestEvictionDomainService)

    Given("만료된 게스트가 3건 존재하는 경우") {
        every { guestEvictionDomainService.evictExpired() } returns 3

        When("execute 를 호출하면") {
            val evictedCount = expireGuestsUseCase.execute()

            Then("GuestEvictionDomainService.evictExpired 가 호출되고 방출 건수를 그대로 반환한다") {
                verify { guestEvictionDomainService.evictExpired() }
                evictedCount shouldBe 3
            }
        }
    }
})
