package com.sportsapp.application.recruitment.usecase

import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 취소 이벤트를 받아 자기 PENDING 모집 신청을 취소한다.
 * cancelPendingApplication 은 이미 CANCELLED 인 신청을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class CancelRecruitmentPaymentUseCase(
    private val recruitmentDomainService: RecruitmentDomainService,
) {
    @Transactional
    fun execute(orderId: Long) {
        recruitmentDomainService.cancelPendingApplication(orderId)
    }
}
