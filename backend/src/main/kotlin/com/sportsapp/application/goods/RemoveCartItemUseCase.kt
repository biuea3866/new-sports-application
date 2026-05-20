package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class RemoveCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(userId: Long, itemId: Long): CartResponse {
        val (cart, items) = cartDomainService.removeItem(userId, itemId)
        return CartResponse.of(cart, items)
    }
}
