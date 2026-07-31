package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * W1-11a 만료 스위퍼의 청크 단위 원자적 커밋 경계.
 *
 * `@Transactional`은 UseCase에 선언한다는 컨벤션에 맞춰, 청크 커밋 트랜잭션을 DomainService가
 * 아니라 이 UseCase가 소유한다. [ExpirePendingGoodsOrdersUseCase]가 청크마다 이 UseCase를
 * 별도 호출하므로 별도 빈 경계로 self-invocation 없이 청크별 독립 트랜잭션이 보장된다
 * (`facility-booking`(W1-11c)의 `ExpireBookingChunkUseCase`와 동일한 구조).
 */
@Service
class ExpireGoodsOrderChunkUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Transactional
    fun execute(orderIds: List<Long>): Int = goodsDomainService.expireOrders(orderIds)
}
