package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.AddCartItemCommand
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.service.CartDomainService
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.orm.ObjectOptimisticLockingFailureException
import org.springframework.retry.annotation.Backoff
import org.springframework.retry.annotation.Retryable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    // 동시 추가 경합: cart/cart_item UNIQUE 충돌 시 fresh tx 로 재시도 → 멱등 병합(addQuantity)으로 수렴.
    @Retryable(
        retryFor = [DataIntegrityViolationException::class, ObjectOptimisticLockingFailureException::class],
        maxAttempts = 12,
        backoff = Backoff(delay = 20, maxDelay = 250, multiplier = 2.0, random = true),
    )
    @Transactional
    fun execute(command: AddCartItemCommand): Pair<Cart, List<CartItem>> =
        cartDomainService.addItem(command.userId, command.productId, command.quantity)
}
