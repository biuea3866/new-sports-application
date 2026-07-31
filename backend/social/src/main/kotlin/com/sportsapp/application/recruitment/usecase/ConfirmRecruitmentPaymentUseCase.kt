package com.sportsapp.application.recruitment.usecase

import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 확정 이벤트를 받아 자기 모집 신청을 CONFIRMED 로 전이한다.
 * confirmApplication 은 이미 CONFIRMED 인 신청을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class ConfirmRecruitmentPaymentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(orderId: Long, paymentId: Long) {
        recruitmentDomainService.confirmApplication(orderId, paymentId)
    }
}
