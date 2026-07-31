package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.ApproveMemberCommand
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.MembershipStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ApproveMemberUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = ApproveMemberUseCase(communityDomainService)

    Given("방장의 승인 커맨드") {
        val pending = CommunityMember.join(communityId = 1L, userId = 2L, isPublic = false)
        pending.approve()
        every { communityDomainService.approve(1L, 100L, 2L) } returns pending

        When("execute 를 호출하면") {
            val result = useCase.execute(ApproveMemberCommand(communityId = 1L, requesterId = 100L, targetUserId = 2L))

            Then("ACTIVE 상태의 CommunityMemberResponse 가 반환된다") {
                result.status shouldBe MembershipStatus.ACTIVE
                verify { communityDomainService.approve(1L, 100L, 2L) }
            }
        }
    }
})
