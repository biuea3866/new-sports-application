package com.sportsapp.presentation.goods.controller

import com.sportsapp.application.goods.usecase.CreateLimitedDropUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropStatsUseCase
import com.sportsapp.application.goods.usecase.GetLimitedDropUseCase
import com.sportsapp.application.goods.usecase.PurchaseLimitedDropUseCase
import com.sportsapp.domain.user.vo.UserPrincipal
import com.sportsapp.presentation.goods.dto.request.CreateLimitedDropRequest
import com.sportsapp.presentation.goods.dto.request.PurchaseLimitedDropRequest
import com.sportsapp.presentation.goods.dto.response.LimitedDropPurchaseResponse
import com.sportsapp.presentation.goods.dto.response.LimitedDropResponse
import com.sportsapp.presentation.goods.dto.response.LimitedDropStatsResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 한정판 REST 진입점(API 계약: TDD "API 계약" 섹션). `limited-drop.enabled=false`면
 * 빈 자체가 등록되지 않아 전 엔드포인트가 404로 응답한다(Release Scenario 2단계·롤백).
 */
@RestController
@RequestMapping("/limited-drops")
@ConditionalOnProperty(name = ["limited-drop.enabled"], havingValue = "true", matchIfMissing = false)
class LimitedDropApiController(
    private val createLimitedDropUseCase: CreateLimitedDropUseCase,
    private val getLimitedDropUseCase: GetLimitedDropUseCase,
    private val purchaseLimitedDropUseCase: PurchaseLimitedDropUseCase,
    private val getLimitedDropStatsUseCase: GetLimitedDropStatsUseCase,
) {

    @PostMapping
    fun createDrop(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestBody request: CreateLimitedDropRequest,
    ): ResponseEntity<LimitedDropResponse> {
        val view = createLimitedDropUseCase.execute(request.toCommand(principal.id))
        return ResponseEntity.status(HttpStatus.CREATED).body(LimitedDropResponse.of(view))
    }

    @GetMapping("/{dropId}")
    fun getDrop(@PathVariable dropId: Long): ResponseEntity<LimitedDropResponse> {
        val view = getLimitedDropUseCase.execute(dropId)
        return ResponseEntity.ok(LimitedDropResponse.of(view))
    }

    @PostMapping("/{dropId}/orders")
    fun purchase(
        @PathVariable dropId: Long,
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestHeader("Idempotency-Key") idempotencyKey: String,
        @RequestBody request: PurchaseLimitedDropRequest,
    ): ResponseEntity<LimitedDropPurchaseResponse> {
        val result = purchaseLimitedDropUseCase.execute(request.toCommand(dropId, principal.id, idempotencyKey))
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(LimitedDropPurchaseResponse.of(result))
    }

    @GetMapping("/{dropId}/stats")
    fun getStats(@PathVariable dropId: Long): ResponseEntity<LimitedDropStatsResponse> {
        val result = getLimitedDropStatsUseCase.execute(dropId)
        return ResponseEntity.ok(LimitedDropStatsResponse.of(result))
    }
}
