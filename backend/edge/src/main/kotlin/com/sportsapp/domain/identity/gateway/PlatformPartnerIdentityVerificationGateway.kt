package com.sportsapp.domain.identity.gateway

import com.sportsapp.domain.identity.vo.PartnerApiCallActivity
import com.sportsapp.domain.identity.vo.PartnerIdentityVerification

/**
 * 파트너 API 키 신원 검증 계약 — edge 가 소유하고, 구현은 단계마다 교체된다 (W1-06b §9).
 *
 * MCP 쪽과 한 인터페이스로 합치지 않고 자격증명별로 나눈다. MCP 검증 구현은 `test-jpa` 프로파일에서
 * 빈이 없어야 하고(오늘 `McpTokenAuthenticationFilter` 의 `@Profile("!test-jpa")` 와 동일한 경계),
 * 파트너 검증 구현은 그 프로파일에서도 존재해야 한다 — 하나로 합치면 두 요구가 충돌해
 * `test-jpa` 풀부팅이 붕괴한다.
 */
interface PlatformPartnerIdentityVerificationGateway {

    fun verify(plainKey: String): PartnerIdentityVerification

    /** 인증 성공한 키의 사용 시각을 기록한다 (요청 critical path 밖에서 비동기 처리됨). */
    fun recordUsage(keyId: Long)

    /** 파트너 API 호출 감사 1건을 기록한다 — 응답이 끝난 뒤(상태코드·지연 확정 후) 호출된다. */
    fun recordActivity(activity: PartnerApiCallActivity)
}
