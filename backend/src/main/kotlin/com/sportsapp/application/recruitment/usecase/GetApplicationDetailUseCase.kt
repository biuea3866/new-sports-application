package com.sportsapp.application.recruitment.usecase

import com.sportsapp.application.recruitment.dto.ApplicationDetailResponse
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 신청 단건 상세 조회. Option A(주문 상세 화면 신설, 사용자 확정 2026-07-09) —
 * 통합 주문내역의 RECRUITMENT 주문상세 화면이 호출하는 단건 조회.
 * 근거: `20260708-상품주문-공유상위컨텍스트-tdd.md` v1.3 "주문 항목 탭 네비게이션 결정" 절.
 * 본인 소유 검증은 [RecruitmentDomainService.getApplicationDetailBy]가 Application.requireOwnedBy로 위임한다.
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
