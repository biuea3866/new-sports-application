package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyCartUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(userId: Long): CartResponse {
        val (cart, items) = cartDomainService.getCartWithItems(userId)
        return CartResponse.of(cart, items)
    }
}
