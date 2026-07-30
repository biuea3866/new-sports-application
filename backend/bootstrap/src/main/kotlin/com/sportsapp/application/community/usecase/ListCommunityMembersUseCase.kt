package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityMemberResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCommunityMembersUseCase(
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long, requesterId: Long): List<CommunityMemberResponse> =
        communityDomainService.findMembers(communityId, requesterId).map(CommunityMemberResponse::of)
}
