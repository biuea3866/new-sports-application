package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropPurchaseResult
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한정판 구매 트랜잭션 경계 — Redis 예약·완충·DB 주문 생성은 전부
 * [LimitedDropDomainService]가 소유하며, 이 UseCase는 오케스트레이션만 담당한다.
 */
@Service
class PurchaseLimitedDropUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Transactional
    fun execute(command: PurchaseLimitedDropCommand): LimitedDropPurchaseResult {
        val (drop, order) = limitedDropDomainService.purchase(command)
        return LimitedDropPurchaseResult.of(order, drop)
    }
}
