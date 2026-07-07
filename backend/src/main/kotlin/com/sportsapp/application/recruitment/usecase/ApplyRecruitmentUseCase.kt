package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationResponse
import com.sportsapp.application.recruitment.dto.ApplyRecruitmentCommand
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 참가비 0원(무료) 모집은 PG 없이 즉시 CONFIRMED 확정한다.
 * 참가비>0 모집은 PENDING 신청까지만 처리한다 — PG 개시(PaymentDomainService.createPending/initiatePg,
 * OrderType.RECRUITMENT)는 아직 배선하지 않는다. OrderType enum에 RECRUITMENT 값이 없어(BE-55/W3 소관)
 * 이 단계에서 결제 도메인을 참조하면 컴파일이 깨지거나 잘못된 타입을 강제하게 되므로 W2에서는 의도적으로
 * 유예한다. BE-55(W3)에서 PG 배선이 추가되면 fee>0 분기에 결제 개시 호출이 이어진다.
 */
@Service
class ApplyRecruitmentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(command: ApplyRecruitmentCommand): ApplicationResponse {
        val recruitment = recruitmentDomainService.getRecruitment(command.recruitmentId)
        val applicationId = recruitmentDomainService.apply(command.recruitmentId, command.applicantUserId)
        val result = confirmIfFree(recruitment.isFree(), applicationId)
        return ApplicationResponse.of(result)
    }

    private fun confirmIfFree(isFree: Boolean, applicationId: Long): Application =
        if (isFree) {
            recruitmentDomainService.confirmApplication(applicationId, null)
        } else {
            recruitmentDomainService.getApplicationById(applicationId)
        }
}
