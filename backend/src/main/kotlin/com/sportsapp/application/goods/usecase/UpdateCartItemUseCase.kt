package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.UpdateCartItemCommand
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.service.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class UpdateCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(command: UpdateCartItemCommand): Pair<Cart, List<CartItem>> =
        cartDomainService.updateItem(command.userId, command.itemId, command.quantity)
}
