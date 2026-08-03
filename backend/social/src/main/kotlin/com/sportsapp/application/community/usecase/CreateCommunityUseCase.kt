package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityResponse
import com.sportsapp.application.community.dto.CreateCommunityCommand
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.user.service.UserDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateCommunityUseCase(
    private val communityDomainService: CommunityDomainService,
    private val userDomainService: UserDomainService,
) {
    @Transactional
    fun execute(command: CreateCommunityCommand): CommunityResponse {
        val community = communityDomainService.create(
            name = command.name,
            description = command.description,
            visibility = command.visibility,
            sportCategory = command.sportCategory,
            hostUserId = command.hostUserId,
        )
        // 개설 직후에는 컨텍스트 방이 아직 provisioning 되지 않았다(BE-09 비동기 이벤트 소비) — roomId는 null.
        val hostNames = userDomainService.findDisplayNamesBy(listOf(command.hostUserId))
        return CommunityResponse.of(community, 1, null, hostNames.of(command.hostUserId))
    }
}
