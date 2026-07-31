package com.sportsapp.infrastructure.security

import com.sportsapp.domain.identity.gateway.PlatformPartnerIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.InternalAuthChannel
import com.sportsapp.domain.identity.vo.InternalIdentity
import com.sportsapp.domain.identity.vo.PartnerApiCallActivity
import com.sportsapp.domain.identity.vo.PartnerIdentityVerification
import com.sportsapp.domain.identity.vo.PartnerIdentityVerificationOutcome
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.MediaType
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.time.ZonedDateTime

/**
 * 파트너 API 키 인증 필터 — W1-06b 로 `bootstrap` 에서 `edge` 로 이동했다.
 *
 * 이동 전에는 platform 소유 `PartnerDomainService`·`UserDomainService`·recorder 2종을 직접 주입했다.
 * 검증·기록을 [PlatformPartnerIdentityVerificationGateway] 뒤로 숨겨 edge 가 platform 을 모르게 한다.
 *
 * 응답 상태·지연이 확정된 뒤 감사를 기록하는 순서(`doFilter` 이후)는 이동 전과 동일하다.
 * `partner_` prefix 가 아닌 Authorization 헤더는 즉시 pass-through — JWT·MCP 인증 경로 무영향.
 */
@Component
class PartnerApiKeyAuthenticationFilter(
    private val platformPartnerIdentityVerificationGateway: PlatformPartnerIdentityVerificationGateway,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val plainKey = resolvePartnerKey(request)
        val keyId = plainKey?.let { parseKeyId(it) }
        if (plainKey == null || keyId == null) {
            filterChain.doFilter(request, response)
            return
        }

        val verification = platformPartnerIdentityVerificationGateway.verify(plainKey)
        if (verification.outcome != PartnerIdentityVerificationOutcome.VALID) {
            writeAuthenticationError(response, verification.outcome)
            return
        }
        platformPartnerIdentityVerificationGateway.recordUsage(keyId)
        authenticate(verification)
        proceedWithAudit(request, response, filterChain, verification)
    }

    private fun authenticate(verification: PartnerIdentityVerification) {
        val authorities = verification.authorities.map { SimpleGrantedAuthority(it) }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(verification.principal, null, authorities)
    }

    private fun proceedWithAudit(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
        verification: PartnerIdentityVerification,
    ) {
        val calledAt = ZonedDateTime.now()
        val startedAtMillis = System.currentTimeMillis()
        filterChain.doFilter(propagateIdentity(request, verification), response)
        platformPartnerIdentityVerificationGateway.recordActivity(
            PartnerApiCallActivity(
                partnerId = requireNotNull(verification.partnerId),
                userId = requireNotNull(verification.linkedUserId),
                httpMethod = request.method,
                requestPath = request.requestURI,
                statusCode = response.status,
                latencyMs = (System.currentTimeMillis() - startedAtMillis).toInt(),
                ipAddr = request.remoteAddr,
                userAgent = request.getHeader("User-Agent"),
                calledAt = calledAt,
            ),
        )
    }

    private fun propagateIdentity(
        request: HttpServletRequest,
        verification: PartnerIdentityVerification,
    ): HttpServletRequest = InternalIdentityHeaderRequest(
        request,
        InternalIdentity(
            subjectId = requireNotNull(verification.linkedUserId),
            channel = InternalAuthChannel.PARTNER_API_KEY,
            scopes = emptyList(),
        ),
    )

    private fun resolvePartnerKey(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith(BEARER_PREFIX) }
            ?.removePrefix(BEARER_PREFIX)

    /** Key format: `partner_<keyId>_<random>`. prefix·형식이 아니면 null → pass-through (이동 전과 동일). */
    private fun parseKeyId(plainKey: String): Long? =
        plainKey.takeIf { it.startsWith(KEY_PREFIX) }
            ?.removePrefix(KEY_PREFIX)
            ?.split("_", limit = 2)
            ?.takeIf { it.size >= 2 }
            ?.first()
            ?.toLongOrNull()

    private fun writeAuthenticationError(
        response: HttpServletResponse,
        outcome: PartnerIdentityVerificationOutcome,
    ) {
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        if (outcome == PartnerIdentityVerificationOutcome.SUSPENDED) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.writer.write("""{"status":403,"title":"Forbidden","detail":"Partner is suspended"}""")
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.writer.write(
            """{"status":401,"title":"Unauthorized","detail":"Invalid or expired partner API key"}""",
        )
    }

    private companion object {
        const val BEARER_PREFIX = "Bearer "
        const val KEY_PREFIX = "partner_"
    }
}
