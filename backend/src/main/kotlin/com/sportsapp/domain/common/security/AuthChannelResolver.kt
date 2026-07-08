package com.sportsapp.domain.common.security

/**
 * "이 요청이 파트너 API Key 인증을 경유했는가" 를 노출하는 인증 채널 신호 계약.
 *
 * sellerType(B2C/B2B) 자동 판별처럼, 요청이 어떤 인증 경로를 거쳤는지가 필요한
 * 하위 레이어(DomainService)가 이 인터페이스를 통해 조회한다.
 *
 * 도메인 layer 에는 interface 만 두고 SecurityContext 의존 구현체는 infrastructure 에서 제공한다.
 */
interface AuthChannelResolver {

    /**
     * 현재 요청이 파트너 API Key 인증(PartnerApiKeyAuthenticationFilter)을 경유했으면 true.
     * JWT 인증 또는 미인증 상태는 false.
     */
    fun isPartnerAuthenticated(): Boolean
}
