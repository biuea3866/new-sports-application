package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.dto.AddCartItemCommand
import com.sportsapp.application.goods.dto.UpdateCartItemCommand
import com.sportsapp.application.goods.usecase.AddCartItemUseCase
import com.sportsapp.application.goods.usecase.ClearCartUseCase
import com.sportsapp.application.goods.usecase.GetMyCartUseCase
import com.sportsapp.application.goods.usecase.RemoveCartItemUseCase
import com.sportsapp.application.goods.usecase.UpdateCartItemUseCase
import com.sportsapp.presentation.goods.dto.request.AddCartItemRequest
import com.sportsapp.presentation.goods.dto.request.UpdateCartItemRequest
import com.sportsapp.presentation.goods.dto.response.CartResponse
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
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
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = getMyCartUseCase.execute(userId)
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @PostMapping("/items")
    fun addCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = addCartItemUseCase.execute(request.toCommand(userId))
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @PatchMapping("/items/{itemId}")
    fun updateCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable itemId: Long,
        @RequestBody request: UpdateCartItemRequest,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = updateCartItemUseCase.execute(request.toCommand(userId, itemId))
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @DeleteMapping("/items/{itemId}")
    fun removeCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable itemId: Long,
    ): ResponseEntity<CartResponse> {
        val (cart, items) = removeCartItemUseCase.execute(userId, itemId)
        return ResponseEntity.ok(CartResponse.of(cart, items))
    }

    @DeleteMapping
    fun clearCart(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<Void> {
        clearCartUseCase.execute(userId)
        return ResponseEntity.noContent().build()
    }
}
