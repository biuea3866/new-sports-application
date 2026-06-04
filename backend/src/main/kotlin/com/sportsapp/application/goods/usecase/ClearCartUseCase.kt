package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.service.CartDomainService
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
