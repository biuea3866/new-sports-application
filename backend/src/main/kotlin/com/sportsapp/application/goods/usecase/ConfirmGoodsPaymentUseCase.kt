package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 확정 이벤트를 받아 자기 상품 주문을 CONFIRMED 로 전이한다.
 * markPaid 는 동일 paymentId 로 이미 CONFIRMED 인 주문을 조용히 반환하므로 중복 수신에 멱등하다.
 */
@Service
class ConfirmGoodsPaymentUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional
    fun execute(orderId: Long, paymentId: Long) {
        goodsDomainService.markPaid(orderId, paymentId)
    }
}
