package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.GoodsDomainService
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * W1-11a 만료 스위퍼의 청크 단위 원자적 커밋 경계.
 *
 * `@Transactional`은 UseCase에 선언한다는 컨벤션에 맞춰, 청크 커밋 트랜잭션을 DomainService가
 * 아니라 이 UseCase가 소유한다. [ExpirePendingGoodsOrdersUseCase]가 청크마다 이 UseCase를
 * 별도 호출하므로 별도 빈 경계로 self-invocation 없이 청크별 독립 트랜잭션이 보장된다
 * (`facility-booking`(W1-11c)의 `ExpireBookingChunkUseCase`와 동일한 구조).
 *
 * **CAS 경합 재시도(재리뷰 p2)**: `facility-booking` 정본의 `expireBookings`는 순수
 * CAS(부수 쓰기 0건)라 경합해도 실패하지 않지만, [GoodsDomainService.expireOrders]는 만료
 * 성공 건마다 `Stock`(`@Version`)에 재고 복원 쓰기를 한다 — goods만 있는 노출이다. 이
 * 청크 트랜잭션 커밋 시점에 동시 구매([com.sportsapp.domain.goods.entity.Stock.deduct] 경로)와
 * 부딪히면 [ObjectOptimisticLockingFailureException]이 던져진다.
 * [PurchaseLimitedDropUseCase]가 동일 예외를 동일 근거(Stock `@Version` 동시 쓰기 경합)로
 * 재시도하고 있어 같은 패턴을 따른다. 재시도로도 해소되지 않으면
 * [ExpirePendingGoodsOrdersUseCase]가 청크 단위로 실패를 격리해 다음 청크로 진행한다.
 */
@Service
class ExpireGoodsOrderChunkUseCase(
    private val goodsDomainService: GoodsDomainService,
) {
    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        maxAttempts = 5,
        backoff = Backoff(delay = 20, maxDelay = 200, multiplier = 2.0, random = true),
    )
    @Transactional
    fun execute(orderIds: List<Long>): Int = goodsDomainService.expireOrders(orderIds)
}
