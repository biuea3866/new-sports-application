package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.BusinessException
import com.sportsapp.domain.common.ErrorStatus
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import com.sportsapp.domain.partner.service.AuthenticatedPartner
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
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
 * Partner API Key(`partner_<keyId>_<random>`)를 검증해 연동 User principal을
 * SecurityContext에 주입하고, 요청 완료 후 [PartnerActivityRecorder]로 감사를 트리거한다.
 *
 * `partner_` prefix가 아닌 Authorization 헤더는 즉시 pass-through — JWT·mcp 인증 경로 무영향.
 * SecurityConfig 필터 등록·matcher 설정은 BE-10에서 담당한다 (이 클래스는 필터 로직만 소유).
 */
@Component
class PartnerApiKeyAuthenticationFilter(
    private val partnerDomainService: PartnerDomainService,
    private val userDomainService: UserDomainService,
    private val partnerActivityRecorder: PartnerActivityRecorder,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val plainKey = resolveKey(request)
        val keyId = plainKey?.let { parseKeyId(it) }

        if (plainKey == null || keyId == null) {
            filterChain.doFilter(request, response)
            return
        }

        val authenticatedPartner = try {
            partnerDomainService.authenticate(keyId, plainKey)
        } catch (exception: BusinessException) {
            writeAuthenticationError(response, exception)
            return
        }

        injectSecurityContext(authenticatedPartner.linkedUserId)
        proceedWithAudit(request, response, filterChain, authenticatedPartner)
    }

    private fun proceedWithAudit(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
        authenticatedPartner: AuthenticatedPartner,
    ) {
        val calledAt = ZonedDateTime.now()
        val startedAtMillis = System.currentTimeMillis()
        filterChain.doFilter(request, response)
        val latencyMs = (System.currentTimeMillis() - startedAtMillis).toInt()
        partnerActivityRecorder.record(
            partnerId = authenticatedPartner.partnerId,
            userId = authenticatedPartner.linkedUserId,
            httpMethod = request.method,
            requestPath = request.requestURI,
            statusCode = response.status,
            latencyMs = latencyMs,
            ipAddr = request.remoteAddr,
            userAgent = request.getHeader("User-Agent"),
            calledAt = calledAt,
        )
    }

    private fun injectSecurityContext(linkedUserId: Long) {
        val linkedUser = userDomainService.findById(linkedUserId)
        val roleNames = userDomainService.getRolesForUser(linkedUserId).map { it.name }
        val principal = UserPrincipal(id = linkedUserId, email = linkedUser.email, roles = roleNames)
        val authorities = roleNames.map { roleName -> SimpleGrantedAuthority("ROLE_$roleName") }
        SecurityContextHolder.getContext().authentication =
            UsernamePasswordAuthenticationToken(principal, null, authorities)
    }

    private fun resolveKey(request: HttpServletRequest): String? {
        val bearerToken = request.getHeader("Authorization") ?: return null
        return bearerToken.takeIf { it.startsWith("Bearer ") }?.removePrefix("Bearer ")
    }

    // Key format: partner_<keyId>_<random>. Returns null when prefix does not match (pass-through).
    private fun parseKeyId(plainKey: String): Long? =
        plainKey.takeIf { it.startsWith(KEY_PREFIX) }
            ?.removePrefix(KEY_PREFIX)
            ?.split("_", limit = 2)
            ?.takeIf { it.size >= 2 }
            ?.get(0)
            ?.toLongOrNull()

    private fun writeAuthenticationError(response: HttpServletResponse, exception: BusinessException) {
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        if (exception.status == ErrorStatus.FORBIDDEN) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.writer.write(
                """{"status":403,"title":"Forbidden","detail":"Partner is suspended"}""",
            )
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.writer.write(
            """{"status":401,"title":"Unauthorized","detail":"Invalid or expired partner API key"}""",
        )
    }

    private companion object {
        const val KEY_PREFIX = "partner_"
    }
}
