package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ClearCartUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(userId: Long) {
        cartDomainService.clearCart(userId)
    }
}
