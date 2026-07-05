package com.sportsapp.application.community.usecase

import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.community.vo.SportCategory
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.message.vo.RoomContextType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class ListMyCommunitiesUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val roomContextQueryService = mockk<RoomContextQueryService>()
    val useCase = ListMyCommunitiesUseCase(communityDomainService, roomContextQueryService)

    Given("내가 ACTIVE 멤버인 커뮤니티가 있는 경우") {
        val community = mockk<Community>(relaxed = true) {
            every { id } returns 1L
            every { name } returns "주말 축구 모임"
            every { description } returns null
            every { visibility } returns CommunityVisibility.PUBLIC
            every { sportCategory } returns SportCategory.SOCCER
            every { currentHostUserId } returns 1L
            every { createdAt } returns ZonedDateTime.now()
        }
        every { communityDomainService.findMyCommunities(300L) } returns listOf(community)
        every { communityDomainService.countActiveMembers(1L) } returns 1
        every { roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, 1L) } returns null

        When("execute 를 호출하면") {
            val result = useCase.execute(userId = 300L)

            Then("내 커뮤니티 목록이 반환된다") {
                result shouldHaveSize 1
            }
        }
    }
})
