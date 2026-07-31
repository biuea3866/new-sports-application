package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.common.GuestRequester
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 모집 목록 조회 — communityId 로 필터할 때 [CommunityDomainService.getCommunity]로 멤버십을
 * 재판정한다(TDD B "GET /recruitments?communityId=: communityId 소속 시 멤버십"). communityId
 * 가 없으면(전역 목록) 인가 없이 통과한다. requesterId 부재는 [GetRecruitmentUseCase]와 동일한
 * 게스트 sentinel 규칙을 따른다.
 */
@Service
class ListRecruitmentsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long?, requesterId: Long? = null): List<RecruitmentResponse> {
        communityId?.let { communityDomainService.getCommunity(it, requesterId ?: GuestRequester.ID) }
        return recruitmentDomainService.listRecruitments(communityId).map(RecruitmentResponse::of)
    }
}
