package com.sportsapp.application.community.usecase

import com.sportsapp.domain.common.vo.SportCategory
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.entity.CommunityMember
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.community.vo.CommunityRole
import com.sportsapp.domain.community.vo.CommunityVisibility
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.entity.User
import com.sportsapp.domain.user.service.UserDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.ZonedDateTime

/**
 * 동아리 상세 화면이 `방장 #68` 대신 방장 표시 이름을, 멤버 목록이 `사용자 #68` 대신 멤버 표시
 * 이름을 보여주기 위한 조합. community 도메인은 user 를 모른 채로 두고, application 레이어가
 * UserDomainService 로 조회해 응답에 싣는다.
 */
class CommunityDisplayNameTest : BehaviorSpec({

    val communityDomainService = mockk<CommunityDomainService>()
    val roomContextQueryService = mockk<RoomContextQueryService>()
    val userDomainService = mockk<UserDomainService>()

    fun displayNamesOf(vararg pairs: Pair<Long, String>): UserDisplayNames =
        UserDisplayNames.from(
            pairs.map { (userId, displayName) ->
                mockk<User>().also {
                    every { it.id } returns userId
                    every { it.displayName } returns displayName
                }
            },
        )

    fun community(id: Long, hostUserId: Long): Community {
        val community = mockk<Community>(relaxed = true)
        every { community.id } returns id
        every { community.name } returns "주말 축구 모임"
        every { community.description } returns null
        every { community.visibility } returns CommunityVisibility.PUBLIC
        every { community.sportCategory } returns SportCategory.SOCCER
        every { community.currentHostUserId } returns hostUserId
        every { community.createdAt } returns ZonedDateTime.now()
        return community
    }

    Given("방장이 있는 동아리") {
        val getCommunityUseCase = GetCommunityUseCase(
            communityDomainService,
            roomContextQueryService,
            userDomainService,
        )
        every { communityDomainService.getCommunity(1L, 5L) } returns community(1L, 68L)
        every { communityDomainService.countActiveMembers(1L) } returns 3
        every { roomContextQueryService.findRoomByContext(any(), 1L) } returns null
        every { userDomainService.findDisplayNamesBy(listOf(68L)) } returns displayNamesOf(68L to "박영희")

        When("상세를 조회하면") {
            val community = getCommunityUseCase.execute(communityId = 1L, requesterId = 5L)

            Then("방장 표시 이름이 함께 반환된다") {
                community.hostUserId shouldBe 68L
                community.hostDisplayName shouldBe "박영희"
            }
        }
    }

    Given("방장이 닉네임을 설정하지 않은 동아리") {
        val getCommunityUseCase = GetCommunityUseCase(
            communityDomainService,
            roomContextQueryService,
            userDomainService,
        )
        every { communityDomainService.getCommunity(2L, 5L) } returns community(2L, 99L)
        every { communityDomainService.countActiveMembers(2L) } returns 1
        every { roomContextQueryService.findRoomByContext(any(), 2L) } returns null
        every { userDomainService.findDisplayNamesBy(listOf(99L)) } returns UserDisplayNames.from(emptyList())

        When("상세를 조회하면") {
            val community = getCommunityUseCase.execute(communityId = 2L, requesterId = 5L)

            Then("내부 식별자 대신 기본 표시 이름을 반환한다") {
                community.hostDisplayName shouldBe User.UNSET_NICKNAME_DISPLAY_NAME
            }
        }
    }

    Given("멤버 3명인 동아리") {
        val listCommunityMembersUseCase = ListCommunityMembersUseCase(communityDomainService, userDomainService)
        fun member(id: Long, userId: Long, role: CommunityRole): CommunityMember {
            val member = mockk<CommunityMember>(relaxed = true)
            every { member.id } returns id
            every { member.communityId } returns 1L
            every { member.userId } returns userId
            every { member.currentRole } returns role
            every { member.isHost } returns (role == CommunityRole.HOST)
            return member
        }
        every { communityDomainService.findMembers(1L, 68L) } returns listOf(
            member(10L, 68L, CommunityRole.HOST),
            member(11L, 71L, CommunityRole.MEMBER),
            member(12L, 99L, CommunityRole.MEMBER),
        )
        every { userDomainService.findDisplayNamesBy(listOf(68L, 71L, 99L)) } returns
            displayNamesOf(68L to "박영희", 71L to "김철수")

        When("멤버 목록을 조회하면") {
            val members = listCommunityMembersUseCase.execute(communityId = 1L, requesterId = 68L)

            Then("멤버별 표시 이름이 반환되고 미설정 멤버는 기본값을 쓴다") {
                members.map { it.displayName } shouldBe
                    listOf("박영희", "김철수", User.UNSET_NICKNAME_DISPLAY_NAME)
            }

            Then("표시 이름 조회는 멤버 수와 무관하게 1회다 (N+1 없음)") {
                verify(exactly = 1) { userDomainService.findDisplayNamesBy(listOf(68L, 71L, 99L)) }
            }
        }
    }
})
