package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.TransferHostCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class TransferHostUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = TransferHostUseCase(communityDomainService)

    Given("방장 권한 위임 커맨드") {
        justRun { communityDomainService.transfer(1L, 100L, 200L) }

        When("execute 를 호출하면") {
            useCase.execute(TransferHostCommand(communityId = 1L, requesterId = 100L, newHostUserId = 200L))

            Then("CommunityDomainService.transfer 가 호출된다") {
                verify { communityDomainService.transfer(1L, 100L, 200L) }
            }
        }
    }
})
