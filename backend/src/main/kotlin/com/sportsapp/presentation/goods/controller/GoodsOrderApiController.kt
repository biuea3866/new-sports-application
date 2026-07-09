package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.usecase.CreateGoodsOrderUseCase
import com.sportsapp.application.goods.usecase.GetGoodsOrderUseCase
import com.sportsapp.application.goods.usecase.ListMyGoodsOrdersUseCase
import com.sportsapp.presentation.goods.dto.request.CreateGoodsOrderRequest
import com.sportsapp.presentation.goods.dto.response.GoodsOrderResponse
import com.sportsapp.presentation.goods.dto.response.GoodsOrderSummaryResponse
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/goods-orders")
class GoodsOrderApiController(
    private val createGoodsOrderUseCase: CreateGoodsOrderUseCase,
    private val getGoodsOrderUseCase: GetGoodsOrderUseCase,
    private val listMyGoodsOrdersUseCase: ListMyGoodsOrdersUseCase,
) {

    @PostMapping
    fun createOrder(
        @RequestHeader("X-User-Id") userId: Long,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: CreateGoodsOrderRequest,
    ): ResponseEntity<GoodsOrderResponse> {
        val result = createGoodsOrderUseCase.execute(request.toCommand(userId, idempotencyKey))
        return ResponseEntity.accepted().body(GoodsOrderResponse.ofCreated(result))
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<GoodsOrderResponse> {
        val detail = getGoodsOrderUseCase.execute(userId, orderId)
        return ResponseEntity.ok(GoodsOrderResponse.of(detail))
    }

    @GetMapping("/me")
    fun listMyOrders(
        @RequestHeader("X-User-Id") userId: Long,
        pageable: Pageable,
    ): ResponseEntity<Page<GoodsOrderSummaryResponse>> {
        val page = listMyGoodsOrdersUseCase.execute(userId, pageable)
        return ResponseEntity.ok(page.map { GoodsOrderSummaryResponse.of(it) })
    }
}
