package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.user.dto.UserDisplayNames
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyCommunitiesUseCase(
    private val communityDomainService: CommunityDomainService,
    private val roomContextQueryService: RoomContextQueryService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<CommunityResponse> {
        val communities = communityDomainService.findMyCommunities(userId)
        val hostNames = userDomainService.findDisplayNamesBy(communities.map { it.currentHostUserId })
        return communities.map { toCommunityResponse(it, hostNames) }
    }

    private fun toCommunityResponse(community: Community, hostNames: UserDisplayNames): CommunityResponse {
        val memberCount = communityDomainService.countActiveMembers(community.id)
        val roomId = roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, community.id)?.id
        return CommunityResponse.of(community, memberCount, roomId, hostNames.of(community.currentHostUserId))
    }
}
