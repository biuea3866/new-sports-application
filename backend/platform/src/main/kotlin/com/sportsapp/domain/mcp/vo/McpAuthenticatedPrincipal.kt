package com.sportsapp.domain.mcp.vo

import com.sportsapp.domain.common.security.AuthenticatedPrincipal

/**
 * MCP 토큰으로 인증된 주체.
 *
 * [AuthenticatedPrincipal] 을 상속해 edge 의 인증 필터가 이 구체 타입을 몰라도 검증 결과 주체를
 * SecurityContext 에 주입할 수 있게 한다 (W1-06b) — edge 는 platform 을 의존하지 않는다.
 */
interface McpAuthenticatedPrincipal : AuthenticatedPrincipal {
    val tokenId: Long
    val userId: Long
    val grantedScopes: Set<McpScope>
}
