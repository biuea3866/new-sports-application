package com.sportsapp.application.community.usecase

import com.sportsapp.application.community.dto.CommunityBookingListItemResponse
import com.sportsapp.domain.community.service.CommunityBookingDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListCommunityBookingsUseCase(
    private val communityBookingDomainService: CommunityBookingDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long, requesterId: Long): List<CommunityBookingListItemResponse> =
        communityBookingDomainService.findLinked(communityId, requesterId).map(CommunityBookingListItemResponse::of)
}
