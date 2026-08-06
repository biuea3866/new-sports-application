package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryCriteria
import com.sportsapp.application.goods.dto.InternalGoodsOrderHistoryItemResponse
import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 통합 주문내역(BE-08)의 `OrderHistoryGateway.findGoodsOrders` 원격 구현(2단계) 공급자
 * (S2-03, `GET /internal/order-history/goods`).
 *
 * 요청자 소유 주문만 반환한다 — 소유권 경계는 [GoodsDomainService.listMyOrdersWithTitle] 조회가
 * 보장한다. `size` 는 절삭 없이 위임한다(파사드가 창을 계산해 넘긴다).
 */
@Service
class FindGoodsOrderHistoryUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional(readOnly = true)
    fun execute(criteria: InternalGoodsOrderHistoryCriteria): List<InternalGoodsOrderHistoryItemResponse> =
        goodsDomainService.listMyOrdersWithTitle(criteria.userId, criteria.toPageable())
            .content
            .map(InternalGoodsOrderHistoryItemResponse::of)
}
