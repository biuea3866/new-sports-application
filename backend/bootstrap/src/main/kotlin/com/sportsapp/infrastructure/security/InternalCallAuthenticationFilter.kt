package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.InternalCallHeaders
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * 서비스 간 내부 호출의 **호출자 인증** 필터 (S2-07 §6-3).
 *
 * 1단계에서 edge 파사드는 로컬 어댑터로 DomainService 를 직접 불렀다. edge 가 별 프로세스가 되면
 * 그 호출이 HTTP 를 타는데, 그때 두 문제가 동시에 생긴다:
 *
 *  1. 내부 경로를 그냥 열면 호스트에서 신원 헤더를 위조해 **남의 주문 이력을 조회**할 수 있다.
 *     dev compose 는 backend 를 8080 에 직접 publish 하므로 nginx 우회 경로가 실재한다.
 *  2. 반대로 닫아두면 edge 의 정상 호출이 401 이 된다.
 *
 * 그래서 공유 시크릿([InternalCallHeaders.CALL_TOKEN])으로 **호출자**를 먼저 인증하고, 통과한
 * 호출에 한해 앞단 [InternalIdentityHeaderSanitizingFilter] 가 폐기한 **신원 헤더를 되살린다.**
 * 되살리기가 없으면 order-history 계열 공급자 엔드포인트가 런타임에서 항상 400 이 된다.
 *
 * 설계 결정:
 * - **실패는 404 다.** 403 은 경로가 존재한다는 사실을 알려준다 — nginx 인그레스가 같은 이유로
 *   404 를 쓴다(`infra/nginx/lb.conf`). 본문도 남기지 않는다.
 * - **토큰 미설정이면 전부 닫는다.** 미주입 상태가 "무방비 개방"이 되는 방향의 사고를 막는다.
 * - **알림 웹훅 경로는 제외한다.** 자체 시크릿(`alerting.webhook-token`)을 이미 검증하므로
 *   기존 Grafana 계약을 깨지 않는다. 대신 그 경로로 들어온 신원 헤더는 되살리지 않는다.
 * - 비교는 [MessageDigest.isEqual] 로 한다 — 문자열 `==` 는 앞자리부터 짧게 끊어 비교해
 *   타이밍으로 토큰을 한 글자씩 좁힐 여지를 준다.
 */
@Component
class InternalCallAuthenticationFilter(
    @Value("\${internal.call-token:}") private val configuredToken: String,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val path = request.requestURI
        if (!isInternalPath(path) || isSelfGuardedPath(path)) {
            filterChain.doFilter(request, response)
            return
        }

        if (!isAuthenticCaller(request.getHeader(InternalCallHeaders.CALL_TOKEN))) {
            response.status = HttpStatus.NOT_FOUND.value()
            return
        }

        filterChain.doFilter(trustIdentityHeaders(request), response)
    }

    /**
     * SecurityConfig 의 와일드카드 matcher 와 **같은 범위**를 판정한다 — 그 matcher 는 하위 경로뿐
     * 아니라 접두사 자체(`/internal/alerts`)도 포함한다. 슬래시를 붙인 접두사 비교만 하면 그 정확
     * 경로가 빠져, 인가는 열려 있는데 이 필터만 404 로 막는 불일치가 생긴다.
     * (알림 내부 raise 진입점이 정확히 그 경로라 시나리오 테스트가 이 갭을 잡았다.)
     */
    private fun matchesBase(path: String, base: String) = path == base || path.startsWith("$base/")

    private fun isInternalPath(path: String) = matchesBase(path, INTERNAL_BASE)

    /** 자체 시크릿으로 이미 보호되는 경로 — 호출자 토큰을 요구하지 않는다. */
    private fun isSelfGuardedPath(path: String) = matchesBase(path, ALERTS_BASE)

    private fun isAuthenticCaller(presentedToken: String?): Boolean {
        if (configuredToken.isEmpty() || presentedToken.isNullOrEmpty()) return false
        return MessageDigest.isEqual(
            presentedToken.toByteArray(StandardCharsets.UTF_8),
            configuredToken.toByteArray(StandardCharsets.UTF_8),
        )
    }

    /**
     * 호출자 인증을 통과했으므로 앞단에서 폐기된 신원 헤더를 되살린다.
     *
     * 되살리기 지점은 폐기 래퍼가 스스로 노출하는 [InternalIdentityHeaderRequest.trustOriginalIdentity]
     * 하나뿐이다 — "누가 폐기를 우회할 수 있는가"를 그 클래스 한 곳에서 읽히게 하려는 것이다.
     * 래퍼가 없는 경로(필터 단독 테스트 등)는 원본 요청을 그대로 쓴다.
     */
    private fun trustIdentityHeaders(request: HttpServletRequest): HttpServletRequest =
        (request as? InternalIdentityHeaderRequest)?.trustOriginalIdentity() ?: request

    private companion object {
        const val INTERNAL_BASE = "/internal"
        const val ALERTS_BASE = "/internal/alerts"
    }
}
