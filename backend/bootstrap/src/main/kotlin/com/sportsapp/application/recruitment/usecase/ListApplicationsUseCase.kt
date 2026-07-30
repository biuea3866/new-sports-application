package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 개설자용 신청 목록 조회. 개설자 검증은 RecruitmentDomainService.findApplications 내부(Recruitment.requireRecruiter)에서 수행한다.
 */
@Service
class ListApplicationsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(recruitmentId: Long, requesterUserId: Long): List<ApplicationResponse> =
        recruitmentDomainService.findApplications(recruitmentId, requesterUserId).map(ApplicationResponse::of)
}
