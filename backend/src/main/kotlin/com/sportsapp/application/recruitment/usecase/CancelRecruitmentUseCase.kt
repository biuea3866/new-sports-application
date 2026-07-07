package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.CancelRecruitmentCommand
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CancelRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(command: CancelRecruitmentCommand): RecruitmentResponse {
        val recruitment = recruitmentDomainService.cancelRecruitment(
            recruitmentId = command.recruitmentId,
            recruiterUserId = command.recruiterUserId,
        )
        return RecruitmentResponse.of(recruitment)
    }
}
