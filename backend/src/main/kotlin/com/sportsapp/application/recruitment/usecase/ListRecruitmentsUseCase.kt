package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ListRecruitmentsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(communityId: Long?): List<RecruitmentResponse> =
        recruitmentDomainService.listRecruitments(communityId).map(RecruitmentResponse::of)
}
