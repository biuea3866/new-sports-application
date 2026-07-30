package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.LimitedDropPurchaseResult
import com.sportsapp.domain.goods.dto.PurchaseLimitedDropCommand
import com.sportsapp.domain.goods.service.LimitedDropDomainService
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 한정판 구매 트랜잭션 경계 — Redis 예약·완충·DB 주문 생성은 전부
 * [LimitedDropDomainService]가 소유하며, 이 UseCase는 오케스트레이션만 담당한다.
 *
 * 동시 당첨자(Redis 게이트를 통과한 최대 [drop.limitedQuantity]명, Redis 장애 폴백 시엔 그 이상)가
 * 전부 같은 상품의 `Stock` 행(@Version)에 동시 기록을 시도한다. 재시도 없이는 대부분이
 * [ObjectOptimisticLockingFailureException]으로 유실돼 실제 성공 건수가 재고보다 훨씬 적게
 * 수렴한다(`AddCartItemUseCase` 등 Cart 동시성 경합의 동일 패턴을 재사용) — `cancel.lua`가
 * 실패 시 멱등 마커를 즉시 삭제해 재시도 진입을 이미 전제하고 있다(스크립트 주석 참조).
 */
@Service
class PurchaseLimitedDropUseCase(
    private val limitedDropDomainService: LimitedDropDomainService,
) {
    @Retryable(
        retryFor = [ObjectOptimisticLockingFailureException::class],
        // 재시도 예산은 app.limited-drop.retry.max-attempts(LimitedDropRetryProperties, 기본 20)로
        // 외부화한다(FIX-02 §③) — 서버측 요청 수명이 커넥션 풀 대기(5s)·LB proxy_read_timeout(30s)보다
        // 확실히 짧아야 하므로 기존 200회(최악 약 20초)를 낮춘다. 빈 이름은 LimitedDropRetryPropertiesConfig가
        // 고정 배선한다.
        maxAttemptsExpression = "#{@limitedDropRetryProperties.maxAttempts}",
        backoff = Backoff(delay = 5, maxDelay = 100, multiplier = 1.5, random = true),
    )
    @Transactional
    fun execute(command: PurchaseLimitedDropCommand): LimitedDropPurchaseResult {
        val (drop, order) = limitedDropDomainService.purchase(command)
        return LimitedDropPurchaseResult.of(order, drop)
    }
}
