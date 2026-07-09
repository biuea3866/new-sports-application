package com.sportsapp.application.order

import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryResponse
import org.springframework.stereotype.Service

/**
 * 통합 주문내역(BE-08) 조회 오케스트레이션(thin). userId(JWT principal)·criteria를 받아
 * [OrderCompositionService]에 위임한다.
 *
 * `@Transactional`을 선언하지 않는다 — 조합 서비스가 4개 도메인 조회를 별도 워커 스레드로
 * 병렬 fan-out하므로(TDD "실패 경로·동시성·멱등" — 도메인 간 공유 트랜잭션 없음), 호출 스레드에
 * 트랜잭션을 여는 것은 각 도메인 조회를 감싸지 못해 의미가 없다. 각 도메인 조회는 자신의
 * DomainService/Repository 경계에서 독립적으로 read-only 처리된다.
 */
@Service
class GetOrderHistoryUseCase(
    private val orderCompositionService: OrderCompositionService,
) {
    fun execute(userId: Long, criteria: OrderHistoryCriteria): OrderHistoryResponse =
        orderCompositionService.history(userId, criteria)
}
