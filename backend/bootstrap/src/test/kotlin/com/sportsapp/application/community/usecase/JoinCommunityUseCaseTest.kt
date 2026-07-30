package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.JoinCommunityCommand
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.MembershipStatus
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class JoinCommunityUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = JoinCommunityUseCase(communityDomainService)

    Given("공개 커뮤니티 가입 커맨드") {
        val member = CommunityMember.join(communityId = 1L, userId = 2L, isPublic = true)
        every { communityDomainService.join(1L, 2L) } returns member

        When("execute 를 호출하면") {
            val result = useCase.execute(JoinCommunityCommand(communityId = 1L, userId = 2L))

            Then("ACTIVE 상태의 CommunityMemberResponse 가 반환된다") {
                result.status shouldBe MembershipStatus.ACTIVE
                verify { communityDomainService.join(1L, 2L) }
            }
        }
    }
})
