package com.sportsapp.presentation.partner.controller

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.application.partner.usecase.VerifyPartnerApiKeyUseCase
import com.sportsapp.presentation.partner.dto.request.VerifyPartnerApiKeyRequest
import jakarta.validation.Valid
import org.springframework.beans.factory.annotation.Value
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
 * `partner.auth.enabled`(`application.yml`, `SecurityConfig.kt`)가 false면 오늘 필터 자체가
 * 체인에 등록되지 않아 파트너 인증이 전혀 동작하지 않는 것과 동일한 의미를 보존한다 — 플래그가
 * 꺼져 있으면 UseCase를 호출하지 않고 항상 무효 결과를 반환한다(`AlertWebhookApiController`의
 * `alertingEnabled` 게이팅과 동일한 위치·패턴).
 *
 * `PartnerApiKeyAuthenticationFilter`(bootstrap)는 이 컨트롤러를 아직 호출하지 않는다 — 필터가
 * 옮겨질 때까지 기존 인증 경로는 무변경으로 동작한다(④ 독립 배포 가능).
 */
@RestController
@RequestMapping("/internal/partner-api-keys")
class PartnerApiKeyVerificationApiController(
    private val verifyPartnerApiKeyUseCase: VerifyPartnerApiKeyUseCase,
    @Value("\${partner.auth.enabled:false}") private val partnerAuthEnabled: Boolean,
) {
    @PostMapping("/verify")
    fun verify(
        @Valid @RequestBody request: VerifyPartnerApiKeyRequest,
    ): VerifyPartnerApiKeyResponse {
        if (!partnerAuthEnabled) return VerifyPartnerApiKeyResponse.disabled()
        return verifyPartnerApiKeyUseCase.execute(request.toCommand())
    }
}
