package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(command: AddCartItemCommand): CartResponse {
        val (cart, items) = cartDomainService.addItem(command.userId, command.productId, command.quantity)
        return CartResponse.of(cart, items)
    }
}
