package com.sportsapp.presentation.order.controller

import com.sportsapp.application.order.GetOrderHistoryUseCase
import com.sportsapp.application.order.dto.OrderHistoryCriteria
import com.sportsapp.application.order.dto.OrderHistoryResponse
import com.sportsapp.domain.common.order.OrderType
import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/**
 * 통합 주문내역(BE-08) 조회 API. `/api/orders`는 SecurityConfig의 `anyRequest().authenticated()`
 * catch-all로 이미 인증이 강제된다 — 명시 matcher 등록은 통합 티켓 BE-09가 담당한다(SecurityConfig.kt
 * 미수정, Single Writer).
 */
@RestController
@RequestMapping("/api/orders")
class OrderHistoryApiController(
    private val getOrderHistoryUseCase: GetOrderHistoryUseCase,
) {
    @GetMapping
    fun getOrderHistory(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(required = false) orderType: OrderType?,
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int,
    ): ResponseEntity<OrderHistoryResponse> {
        val criteria = OrderHistoryCriteria(orderType = orderType, status = status, page = page, size = size)
        val response = getOrderHistoryUseCase.execute(principal.id, criteria)
        return ResponseEntity.ok(response)
    }
}
