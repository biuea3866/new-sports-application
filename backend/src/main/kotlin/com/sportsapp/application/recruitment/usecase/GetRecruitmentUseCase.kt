package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.common.GuestRequester
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.community.service.CommunityDomainService
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 모집 상세 조회 — 소속 community 가 있으면 [CommunityDomainService.getCommunity]로 가시성을
 * 재판정한다(TDD B "GET /recruitments/{id}: 소속 community visibility"). requesterId 가
 * 없으면(비로그인) [GuestRequester.ID] sentinel 을 전달해 PRIVATE 모임은 항상 비멤버로
 * 거부되게 한다 — [com.sportsapp.application.post.usecase.GetPostUseCase]와 동일한 패턴이다.
 */
@Service
class GetRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
    private val communityDomainService: CommunityDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(recruitmentId: Long, requesterId: Long? = null): RecruitmentResponse {
        val recruitment = recruitmentDomainService.getRecruitment(recruitmentId)
        recruitment.communityId?.let { communityDomainService.getCommunity(it, requesterId ?: GuestRequester.ID) }
        return RecruitmentResponse.of(recruitment)
    }
}
