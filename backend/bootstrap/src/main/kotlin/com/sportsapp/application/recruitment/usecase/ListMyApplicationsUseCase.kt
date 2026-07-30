package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신청자 본인 신청 목록 조회 (FR-4 취소 FE 경로 지탱).
 */
@Service
class ListMyApplicationsUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(applicantUserId: Long): List<ApplicationResponse> =
        recruitmentDomainService.findApplicationsBy(applicantUserId).map(ApplicationResponse::of)
}
