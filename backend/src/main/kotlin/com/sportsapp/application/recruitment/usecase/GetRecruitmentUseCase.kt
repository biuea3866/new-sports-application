package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(recruitmentId: Long): RecruitmentResponse =
        RecruitmentResponse.of(recruitmentDomainService.getRecruitment(recruitmentId))
}
