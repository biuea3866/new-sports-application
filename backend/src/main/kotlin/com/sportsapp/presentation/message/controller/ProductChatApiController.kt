package com.sportsapp.presentation.message.controller

import com.sportsapp.application.message.dto.CreateGoodsTradeRoomCommand
import com.sportsapp.application.message.usecase.CreateGoodsTradeRoomUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.message.dto.response.RoomResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * goods 거래 채팅 진입점 (BE-11, TDD FR-18) — `/products/{productId}/chat`.
 */
@RestController
@RequestMapping("/products")
class ProductChatApiController(
    private val createGoodsTradeRoomUseCase: CreateGoodsTradeRoomUseCase,
) {

    @PostMapping("/{productId}/chat")
    fun createTradeChat(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable productId: Long,
    ): ResponseEntity<RoomResponse> {
        val command = CreateGoodsTradeRoomCommand(productId = productId, buyerId = principal.id)
        val room = createGoodsTradeRoomUseCase.execute(command)
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.of(room))
    }
}
