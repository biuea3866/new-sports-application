package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationDetailResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신청 단건 상세 조회 (통합 주문내역 RECRUITMENT 탭 → 주문상세). 본인 소유 검증은
 * [RecruitmentDomainService.getApplicationDetailBy]가 Application.requireOwnedBy로 위임한다.
 */
@Service
class GetApplicationDetailUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(applicationId: Long, requesterUserId: Long): ApplicationDetailResponse =
        ApplicationDetailResponse.of(
            recruitmentDomainService.getApplicationDetailBy(applicationId, requesterUserId),
        )
}
