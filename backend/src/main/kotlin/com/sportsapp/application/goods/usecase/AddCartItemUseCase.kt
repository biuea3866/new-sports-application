package com.sportsapp.application.goods.usecase

import com.sportsapp.application.goods.dto.AddCartItemCommand
import com.sportsapp.domain.goods.entity.Cart
import com.sportsapp.domain.goods.entity.CartItem
import com.sportsapp.domain.goods.service.CartDomainService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AddCartItemUseCase(
    private val cartDomainService: CartDomainService,
) {
    @Transactional
    fun execute(command: AddCartItemCommand): Pair<Cart, List<CartItem>> =
        cartDomainService.addItem(command.userId, command.productId, command.quantity)
}
