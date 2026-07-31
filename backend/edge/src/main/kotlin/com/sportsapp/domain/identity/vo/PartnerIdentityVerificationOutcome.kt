package com.sportsapp.domain.identity.vo

/**
 * 파트너 API 키 검증 판정 (W1-06b).
 *
 * 오늘 `PartnerApiKeyAuthenticationFilter` 가 외부에 구분해 노출하는 두 응답(401/403)에 정확히 대응한다.
 */
enum class PartnerIdentityVerificationOutcome {
    VALID,
    INVALID,
    SUSPENDED,
}
