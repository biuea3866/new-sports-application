package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.AddCartItemUseCase
import com.sportsapp.application.goods.CartResponse
import com.sportsapp.application.goods.ClearCartUseCase
import com.sportsapp.application.goods.GetMyCartUseCase
import com.sportsapp.application.goods.RemoveCartItemUseCase
import com.sportsapp.application.goods.UpdateCartItemUseCase
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
    ): ResponseEntity<CartResponse> =
        ResponseEntity.ok(getMyCartUseCase.execute(userId))

    @PostMapping("/items")
    fun addCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestBody request: AddCartItemRequest,
    ): ResponseEntity<CartResponse> =
        ResponseEntity.ok(addCartItemUseCase.execute(request.toCommand(userId)))

    @PatchMapping("/items/{itemId}")
    fun updateCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable itemId: Long,
        @RequestBody request: UpdateCartItemRequest,
    ): ResponseEntity<CartResponse> =
        ResponseEntity.ok(updateCartItemUseCase.execute(request.toCommand(userId, itemId)))

    @DeleteMapping("/items/{itemId}")
    fun removeCartItem(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable itemId: Long,
    ): ResponseEntity<CartResponse> =
        ResponseEntity.ok(removeCartItemUseCase.execute(userId, itemId))

    @DeleteMapping
    fun clearCart(
        @RequestHeader("X-User-Id") userId: Long,
    ): ResponseEntity<Void> {
        clearCartUseCase.execute(userId)
        return ResponseEntity.noContent().build()
    }
}
