package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.InternalRecruitmentApplicationHistoryResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 주문내역(BE-08)이 fan-out 하는 recruitment 신청 이력 원격 공급 UseCase (S2-05).
 * `applicantUserId` 소유 신청만 반환한다 — 소유권 경계는 [RecruitmentDomainService.listApplicationsWithTitleBy]가 보장한다.
 */
@Service
class ListRecruitmentApplicationsForOrderHistoryUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(applicantUserId: Long): List<InternalRecruitmentApplicationHistoryResponse> =
        recruitmentDomainService.listApplicationsWithTitleBy(applicantUserId)
            .map(InternalRecruitmentApplicationHistoryResponse::of)
}
