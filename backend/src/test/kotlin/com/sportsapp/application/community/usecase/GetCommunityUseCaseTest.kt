package com.sportsapp.application.community.usecase

import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.message.entity.Room
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.message.vo.RoomType
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import java.time.ZonedDateTime

class GetCommunityUseCaseTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val roomContextQueryService = mockk<RoomContextQueryService>()
    val useCase = GetCommunityUseCase(communityDomainService, roomContextQueryService)

    fun mockCommunity(communityId: Long) = mockk<Community>(relaxed = true) {
        every { id } returns communityId
        every { name } returns "주말 축구 모임"
        every { description } returns null
        every { visibility } returns CommunityVisibility.PUBLIC
        every { sportCategory } returns SportCategory.SOCCER
        every { currentHostUserId } returns 1L
        every { createdAt } returns ZonedDateTime.now()
    }

    Given("컨텍스트 방이 아직 provisioning 되지 않은 커뮤니티") {
        val community = mockCommunity(communityId = 1L)
        every { communityDomainService.getCommunity(1L, 999L) } returns community
        every { communityDomainService.countActiveMembers(1L) } returns 3
        every { roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, 1L) } returns null

        When("execute 를 호출하면") {
            val result = useCase.execute(communityId = 1L, requesterId = 999L)

            Then("roomId 는 null 이다") {
                result.memberCount shouldBe 3
                result.roomId shouldBe null
            }
        }
    }

    Given("컨텍스트 방이 이미 provisioning 된 커뮤니티") {
        val community = mockCommunity(communityId = 2L)
        val room = Room.createForContext(RoomType.GROUP, RoomContextType.COMMUNITY, 2L, "러닝크루")
        every { communityDomainService.getCommunity(2L, 999L) } returns community
        every { communityDomainService.countActiveMembers(2L) } returns 5
        every { roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, 2L) } returns room

        When("execute 를 호출하면") {
            val result = useCase.execute(communityId = 2L, requesterId = 999L)

            Then("연결된 roomId 가 채워진다") {
                result.roomId shouldBe room.id
            }
        }
    }
})
