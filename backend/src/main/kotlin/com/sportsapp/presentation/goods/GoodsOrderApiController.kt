package com.sportsapp.presentation.goods

import com.sportsapp.application.goods.CreateGoodsOrderUseCase
import com.sportsapp.application.goods.GetGoodsOrderUseCase
import com.sportsapp.application.goods.GoodsOrderResponse
import com.sportsapp.application.goods.GoodsOrderSummaryResponse
import com.sportsapp.application.goods.ListMyGoodsOrdersUseCase
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
        val response = createGoodsOrderUseCase.execute(request.toCommand(userId, idempotencyKey))
        return ResponseEntity.accepted().body(response)
    }

    @GetMapping("/{orderId}")
    fun getOrder(
        @RequestHeader("X-User-Id") userId: Long,
        @PathVariable orderId: Long,
    ): ResponseEntity<GoodsOrderResponse> =
        ResponseEntity.ok(getGoodsOrderUseCase.execute(userId, orderId))

    @GetMapping("/me")
    fun listMyOrders(
        @RequestHeader("X-User-Id") userId: Long,
        pageable: Pageable,
    ): ResponseEntity<Page<GoodsOrderSummaryResponse>> =
        ResponseEntity.ok(listMyGoodsOrdersUseCase.execute(userId, pageable))
}
