package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.application.mcp.usecase.VerifyMcpTokenUseCase
import com.sportsapp.presentation.mcp.dto.request.VerifyMcpTokenRequest
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * platform 내부 신원 검증 API (W1-06a, TDD §6-3 신뢰 경계 — C10).
 *
 * `/internal` 하위 전체 경로는 외부 인그레스에 노출하지 않는 서비스 간 전용 네임스페이스다
 * (선례: `AlertWebhookApiController`의 `/internal/alerts`). 이 컨트롤러 자체는 외부 인그레스
 * 차단(W1-08)·서비스 포트 미개방(W1-02)·SecurityConfig 예외 등록(W1-06b, 체인 재구성 시)을
 * 담당하지 않는다 — 경로 규약만 정의한다.
 *
 * `McpTokenAuthenticationFilter`(bootstrap)는 이 컨트롤러를 아직 호출하지 않는다 — 필터가
 * 옮겨질 때까지 기존 인증 경로는 무변경으로 동작한다(④ 독립 배포 가능).
 */
@RestController
@RequestMapping("/internal/mcp-tokens")
class McpTokenVerificationApiController(
    private val verifyMcpTokenUseCase: VerifyMcpTokenUseCase,
) {
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody request: VerifyMcpTokenRequest,
    ): VerifyMcpTokenResponse = verifyMcpTokenUseCase.execute(request.toCommand())
}
