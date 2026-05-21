package com.sportsapp.application.goods

import com.sportsapp.domain.goods.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(command: UpdateCartItemCommand): CartResponse {
        val (cart, items) = cartDomainService.updateItem(command.userId, command.itemId, command.quantity)
        return CartResponse.of(cart, items)
    }
}
