package com.sportsapp.application.goods.usecase

import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.service.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GetMyCartUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(userId: Long): Pair<Cart, List<CartItem>> =
        cartDomainService.getCartWithItems(userId)
}
