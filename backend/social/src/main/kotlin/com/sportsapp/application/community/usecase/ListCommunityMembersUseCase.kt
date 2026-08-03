package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCommunityMembersUseCase(
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long, requesterId: Long): List<CommunityMemberResponse> {
        val members = communityDomainService.findMembers(communityId, requesterId)
        val memberNames = userDomainService.findDisplayNamesBy(members.map { it.userId })
        return members.map { CommunityMemberResponse.of(it, memberNames.of(it.userId)) }
    }
}
