package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.AddCartItemCommand
import com.sportsapp.application.goods.dto.UpdateCartItemCommand
import com.sportsapp.application.goods.usecase.AddCartItemUseCase
import com.sportsapp.application.goods.usecase.ClearCartUseCase
import com.sportsapp.application.goods.usecase.GetMyCartUseCase
import com.sportsapp.application.goods.usecase.RemoveCartItemUseCase
import com.sportsapp.application.goods.usecase.UpdateCartItemUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.goods.dto.request.AddCartItemRequest
import com.sportsapp.presentation.goods.dto.request.UpdateCartItemRequest
import com.sportsapp.presentation.goods.dto.response.CartResponse
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/cart")
class CartApiController(
    private val getMyCartUseCase: GetMyCartUseCase,
    private val addCartItemUseCase: AddCartItemUseCase,
    private val updateCartItemUseCase: UpdateCartItemUseCase,
    private val removeCartItemUseCase: RemoveCartItemUseCase,
    private val clearCartUseCase: ClearCartUseCase,
) {

    @GetMapping("/me")
    fun getMyCart(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = getMyCartUseCase.execute(principal.id)
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @PostMapping("/items")
    fun addCartItem(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = addCartItemUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @PatchMapping("/items/{itemId}")
    fun updateCartItem(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable itemId: Long,
        @RequestBody request: UpdateCartItemRequest,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = updateCartItemUseCase.execute(request.toCommand(principal.id, itemId))
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @DeleteMapping("/items/{itemId}")
    fun removeCartItem(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable itemId: Long,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = removeCartItemUseCase.execute(principal.id, itemId)
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @DeleteMapping
    fun clearCart(
        @AuthenticationPrincipal principal: UserPrincipal,
    ): ResponseEntity<Void> {
        clearCartUseCase.execute(principal.id)
        return ResponseEntity.noContent().build()
    }
}
