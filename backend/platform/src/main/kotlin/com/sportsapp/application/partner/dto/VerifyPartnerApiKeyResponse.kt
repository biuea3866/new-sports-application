package com.sportsapp.application.partner.dto

import com.sportsapp.domain.partner.service.PartnerApiKeyVerification
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure

/**
 * platform 내부 신원 검증 API(W1-06a) 응답. edge(W1-06b 이후)가 401/403 판단 근거로 쓴다.
 *
 * `failureReason`은 기존 `PartnerApiKeyAuthenticationFilter`가 오늘 이미 외부로 구분해
 * 노출하는 것(SUSPENDED→403)만 남긴다 — 그 외 실패(키 없음·해시 불일치·키 REVOKED·연동 유저
 * 없음)는 전부 INVALID로 수렴한다. 존재하지 않는 키와 폐기된 키의 응답이 동일하다.
 */
data class VerifyPartnerApiKeyResponse(
    val valid: Boolean,
    val partnerId: Long?,
    val linkedUserId: Long?,
    val failureReason: PartnerApiKeyVerificationFailure?,
) {
    companion object {
        fun of(verification: PartnerApiKeyVerification): VerifyPartnerApiKeyResponse = VerifyPartnerApiKeyResponse(
            valid = verification.valid,
            partnerId = verification.partnerId,
            linkedUserId = verification.linkedUserId,
            failureReason = verification.failureReason,
        )

        fun disabled(): VerifyPartnerApiKeyResponse = VerifyPartnerApiKeyResponse(
            valid = false,
            partnerId = null,
            linkedUserId = null,
            failureReason = PartnerApiKeyVerificationFailure.INVALID,
        )
    }
}
