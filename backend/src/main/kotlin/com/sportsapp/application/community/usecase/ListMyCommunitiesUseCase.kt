package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.domain.community.entity.Community
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.message.vo.RoomContextType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListMyCommunitiesUseCase(
    private val communityDomainService: CommunityDomainService,
    private val roomContextQueryService: RoomContextQueryService,
) {
    @Transactional(readOnly = true)
    fun execute(userId: Long): List<CommunityResponse> =
        communityDomainService.findMyCommunities(userId).map(::toCommunityResponse)

    private fun toCommunityResponse(community: Community): CommunityResponse {
        val memberCount = communityDomainService.countActiveMembers(community.id)
        val roomId = roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, community.id)?.id
        return CommunityResponse.of(community, memberCount, roomId)
    }
}
