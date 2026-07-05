package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.KickMemberCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

class KickMemberUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = KickMemberUseCase(communityDomainService)

    Given("방장의 강퇴 커맨드") {
        justRun { communityDomainService.kick(1L, 100L, 2L) }

        When("execute 를 호출하면") {
            useCase.execute(KickMemberCommand(communityId = 1L, requesterId = 100L, targetUserId = 2L))

            Then("CommunityDomainService.kick 이 호출된다") {
                verify { communityDomainService.kick(1L, 100L, 2L) }
            }
        }
    }
})
