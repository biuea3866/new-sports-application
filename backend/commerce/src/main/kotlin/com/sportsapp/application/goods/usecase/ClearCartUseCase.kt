package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.CartDomainService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ClearCartUseCase(
    private val cartDomainService: CartDomainService,
) {
    // 최초 접근 시 cart 동시 생성 경합: UNIQUE 충돌 시 fresh tx 재시도.
    @Retryable(
        retryFor = [DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class],
        maxAttempts = 12,
        backoff = Backoff(delay = 20, maxDelay = 250, multiplier = 2.0, random = true),
    )
    @Transactional
    fun execute(userId: Long) {
        cartDomainService.clearCart(userId)
    }
}
