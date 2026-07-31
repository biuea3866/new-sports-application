package com.sportsapp.infrastructure.security

import com.sportsapp.domain.identity.gateway.PlatformMcpIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.InternalAuthChannel
import com.sportsapp.domain.identity.vo.InternalIdentity
import com.sportsapp.domain.identity.vo.McpIdentityVerification
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Profile
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * MCP 토큰 인증 필터 — W1-06b 로 `bootstrap` 에서 `edge` 로 이동했다.
 *
 * 이동 전에는 platform 소유 Repository 3종(`McpTokenRepository`·`McpTokenScopeRepository`·
 * `PermissionRepository`)을 직접 주입해 전 서비스 공통 인증 체인에서 platform 테이블을 읽었다.
 * W1-04 의 GRANT 로 그 접근이 물리 차단되면 6개 서비스가 기동 즉시 권한 오류가 나므로,
 * 검증을 [PlatformMcpIdentityVerificationGateway] 뒤로 숨겨 결합을 끊었다 (0단계가 남긴 마지막
 * 교차 Repository 주입 = 7번째 결합).
 *
 * `@Profile("!test-jpa")` 는 이동 전과 동일하게 유지한다 — 이 프로파일은 platform 영속 빈을
 * 제외하므로 검증 게이트웨이 구현(조립자 어댑터)도 존재하지 않는다. 기능 토글이 아니라
 * 테스트 슬라이스 경계이므로 `no-conditional-on-property` 대상이 아니다.
 */
@Component
@Profile("!test-jpa")
class McpTokenAuthenticationFilter(
    private val platformMcpIdentityVerificationGateway: PlatformMcpIdentityVerificationGateway,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val plainToken = resolveMcpToken(request)
        if (plainToken == null) {
            filterChain.doFilter(request, response)
            return
        }

        val verification = platformMcpIdentityVerificationGateway.verify(plainToken)
        if (!verification.valid) {
            writeUnauthorized(response)
            return
        }
        authenticate(verification)
        filterChain.doFilter(propagateIdentity(request, verification), response)
    }

    private fun authenticate(verification: McpIdentityVerification) {
        val authorities = verification.authorities.map { SimpleGrantedAuthority(it) }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(verification.principal, null, authorities)
        platformMcpIdentityVerificationGateway.recordUsage(requireNotNull(verification.subjectId))
    }

    private fun propagateIdentity(
        request: HttpServletRequest,
        verification: McpIdentityVerification,
    ): HttpServletRequest = InternalIdentityHeaderRequest(
        request,
        InternalIdentity(
            subjectId = requireNotNull(verification.subjectId),
            channel = InternalAuthChannel.MCP_TOKEN,
            scopes = verification.scopes,
        ),
    )

    /**
     * MCP 자격증명 형태(`mcp_<id>_<random>`)일 때만 평문 토큰을 돌려준다. 그 외(헤더 없음·Bearer 아님·
     * 다른 prefix·id 가 숫자 아님·구분자 없음)는 null 로 pass-through 시킨다 — JWT 요청이 401 이 되지
     * 않도록 이 판별은 반드시 필터가 소유해야 한다 (게이트웨이에 넘기면 무효 판정이 되어 401).
     */
    private fun resolveMcpToken(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization")?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)
            ?: return null
        val hasTokenIdSegment = bearerToken.takeIf { it.startsWith(TOKEN_PREFIX) }
            ?.removePrefix(TOKEN_PREFIX)
            ?.split("_", limit = 2)
            ?.takeIf { it.size >= 2 }
            ?.first()
            ?.toLongOrNull() != null
        return bearerToken.takeIf { hasTokenIdSegment }
    }

    private fun writeUnauthorized(response: HttpServletResponse) {
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            """{"status":401,"title":"Unauthorized","detail":"Invalid or expired MCP token"}""",
        )
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val TOKEN_PREFIX = "mcp_"
    }
}
