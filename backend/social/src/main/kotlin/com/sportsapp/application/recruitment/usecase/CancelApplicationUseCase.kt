package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.CancelApplicationCommand
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CancelApplicationUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(command: CancelApplicationCommand): ApplicationResponse {
        val application = recruitmentDomainService.cancelApplication(
            applicationId = command.applicationId,
            applicantUserId = command.applicantUserId,
        )
        return ApplicationResponse.of(application)
    }
}
