package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.message.service.RoomContextQueryService
import com.sportsapp.domain.message.vo.RoomContextType
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetCommunityUseCase(
    private val communityDomainService: CommunityDomainService,
    private val roomContextQueryService: RoomContextQueryService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long, requesterId: Long): CommunityResponse {
        val community = communityDomainService.getCommunity(communityId, requesterId)
        val memberCount = communityDomainService.countActiveMembers(communityId)
        val roomId = roomContextQueryService.findRoomByContext(RoomContextType.COMMUNITY, communityId)?.id
        val hostNames = userDomainService.findDisplayNamesBy(listOf(community.currentHostUserId))
        return CommunityResponse.of(community, memberCount, roomId, hostNames.of(community.currentHostUserId))
    }
}
