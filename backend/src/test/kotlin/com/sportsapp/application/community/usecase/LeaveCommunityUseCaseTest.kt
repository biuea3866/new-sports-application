package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.LeaveCommunityCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class LeaveCommunityUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = LeaveCommunityUseCase(communityDomainService)

    Given("일반 멤버의 탈퇴 커맨드") {
        justRun { communityDomainService.leave(1L, 2L) }

        When("execute 를 호출하면") {
            useCase.execute(LeaveCommunityCommand(communityId = 1L, userId = 2L))

            Then("CommunityDomainService.leave 가 호출된다") {
                verify { communityDomainService.leave(1L, 2L) }
            }
        }
    }
})
