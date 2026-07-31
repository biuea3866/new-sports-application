package com.sportsapp.infrastructure.identity

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.application.mcp.usecase.VerifyMcpTokenUseCase
import com.sportsapp.domain.identity.gateway.PlatformMcpIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.McpIdentityVerification
import com.sportsapp.domain.mcp.service.McpTokenDomainService
import com.sportsapp.domain.mcp.vo.McpScope
import com.sportsapp.infrastructure.security.McpUserPrincipal
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

/**
 * 1단계 로컬 어댑터 — edge 의 MCP 신원 검증 계약을 같은 프로세스의 platform UseCase 호출로 만족시킨다.
 *
 * **조립자(`bootstrap`)에 두는 이유**: edge 는 platform 을 의존하지 않아야 하고(W1-01a 모듈 그래프),
 * platform 도 edge 를 의존하지 않는다. 두 모듈을 모두 아는 유일한 지점이 컴포지션 루트다.
 * 2단계에는 이 어댑터를 버리고 edge 안의 RestClient 구현이 `/internal/mcp-tokens/verify` 를 호출한다
 * (§9 Branch By Abstraction — 계약은 그대로, 구현만 교체).
 *
 * `@Profile("!test-jpa")` 는 이동 전 필터와 동일한 경계다 — 그 프로파일은 platform 영속 빈을 제외한다.
 */
@Component
@Profile("!test-jpa")
class LocalPlatformMcpIdentityVerificationAdapter(
    private val verifyMcpTokenUseCase: VerifyMcpTokenUseCase,
    private val mcpTokenDomainService: McpTokenDomainService,
) : PlatformMcpIdentityVerificationGateway {

    override fun verify(plainToken: String): McpIdentityVerification {
        val response = verifyMcpTokenUseCase.execute(VerifyMcpTokenCommand(plainToken))
        if (!response.valid) return McpIdentityVerification.invalid()
        return toVerification(response)
    }

    override fun recordUsage(subjectId: Long) = mcpTokenDomainService.recordUsage(subjectId)

    private fun toVerification(response: VerifyMcpTokenResponse): McpIdentityVerification {
        val scopes = response.scopes.map { McpScope.of(it) }
        val tokenId = requireNotNull(response.tokenId) { "verified MCP token must carry tokenId" }
        return McpIdentityVerification.valid(
            principal = McpUserPrincipal(
                tokenId = tokenId,
                userId = requireNotNull(response.userId) { "verified MCP token must carry userId" },
                grantedScopes = scopes.toSet(),
            ),
            authorities = toAuthorities(scopes),
            // 사용 기록 대상이 토큰이므로 주체 식별자는 tokenId 다 (이동 전 필터와 동일).
            subjectId = tokenId,
            scopes = response.scopes,
        )
    }

    /** 이동 전 필터의 권한 문자열 규칙을 그대로 보존한다 — `MCP_SCOPE_{VERB}_{DOMAIN}` + `ROLE_MCP_TOKEN`. */
    private fun toAuthorities(scopes: List<McpScope>): List<String> =
        scopes.map { "MCP_SCOPE_${it.verb.uppercase()}_${it.domain.uppercase()}" } + listOf("ROLE_MCP_TOKEN")
}
