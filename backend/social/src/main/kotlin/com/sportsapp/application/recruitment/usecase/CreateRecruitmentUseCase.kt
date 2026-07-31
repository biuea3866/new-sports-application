package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.CreateRecruitmentCommand
import com.sportsapp.application.recruitment.dto.RecruitmentResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class CreateRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(command: CreateRecruitmentCommand): RecruitmentResponse {
        val recruitment = recruitmentDomainService.create(
            title = command.title,
            description = command.description,
            capacity = command.capacity,
            feeAmount = command.feeAmount,
            activityAt = command.activityAt,
            applicationDeadline = command.applicationDeadline,
            communityId = command.communityId,
            recruiterUserId = command.recruiterUserId,
        )
        return RecruitmentResponse.of(recruitment)
    }
}
