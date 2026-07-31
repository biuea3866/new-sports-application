package com.sportsapp.domain.identity.gateway

import com.sportsapp.domain.identity.vo.McpIdentityVerification

/**
 * MCP 토큰 신원 검증 계약 — edge 가 소유하고, 구현은 단계마다 교체된다 (W1-06b §9 Branch By Abstraction).
 *
 * - 1단계: 조립자(`bootstrap`)의 로컬 어댑터가 platform UseCase 를 같은 프로세스에서 직접 호출한다.
 *   동작은 이동 전과 완전히 동일하다(원격 홉 0).
 * - 2단계: edge 안의 RestClient 어댑터가 platform `/internal/mcp-tokens/verify` 를 호출한다(C10).
 *
 * `~Gateway` 네이밍이 맞다 — 2단계에 **다른 서비스** 호출이 된다. 내부 컨텍스트 협력을 Gateway 로
 * 위장하는 경우가 아니다.
 *
 * **edge 가 platform 을 의존하지 않는 것이 이 인터페이스의 존재 이유다.** 필터가 platform 의
 * Repository 3종을 직접 주입하던 구조(0단계가 남긴 마지막 교차 Repository 주입)를 이 계약이 끊는다 —
 * W1-04 의 GRANT 로 platform 테이블이 물리 차단되면 그 구조는 기동 즉시 권한 오류였다.
 */
interface PlatformMcpIdentityVerificationGateway {

    fun verify(plainToken: String): McpIdentityVerification

    /**
     * 인증 성공한 토큰의 사용 시각을 기록한다. 검증과 분리한 이유는 오늘 필터의 순서를 보존하기
     * 위해서다 — 검증 성공 후에만 기록하고, 기록 실패가 인증 판정을 되돌리지 않는다.
     */
    fun recordUsage(subjectId: Long)
}
