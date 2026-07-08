package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 결제 취소 이벤트를 받아 자기 PENDING 주문을 취소하고 재고를 복원한다.
 * cancelPendingOrder 는 이미 CANCELLED 인 주문을 조용히 반환하므로 중복 수신에 멱등하다(재고 이중 복원 방지).
 */
@Service
class CancelGoodsPaymentUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional
    fun execute(orderId: Long) {
        goodsDomainService.cancelPendingOrder(orderId)
    }
}
