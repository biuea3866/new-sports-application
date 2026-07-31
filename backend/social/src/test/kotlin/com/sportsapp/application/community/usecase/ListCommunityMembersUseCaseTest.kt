package com.sportsapp.application.community.usecase

import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.exception.NotCommunityMemberException
import com.sportsapp.domain.community.service.CommunityDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk

class ListCommunityMembersUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val useCase = ListCommunityMembersUseCase(communityDomainService)

    Given("ACTIVE 멤버가 목록을 조회하는 경우") {
        val member = CommunityMember.join(communityId = 1L, userId = 2L, isPublic = true)
        every { communityDomainService.findMembers(1L, 2L) } returns listOf(member)

        When("execute 를 호출하면") {
            val result = useCase.execute(communityId = 1L, requesterId = 2L)

            Then("멤버 목록이 반환된다") {
                result shouldHaveSize 1
            }
        }
    }

    Given("커뮤니티 멤버가 아닌 사용자가 목록을 조회하는 경우 (FR-13 ②)") {
        every { communityDomainService.findMembers(1L, 999L) } throws NotCommunityMemberException(1L, 999L)

        When("execute 를 호출하면") {
            Then("NotCommunityMemberException 이 발생한다") {
                shouldThrow<NotCommunityMemberException> {
                    useCase.execute(communityId = 1L, requesterId = 999L)
                }
            }
        }
    }
})
