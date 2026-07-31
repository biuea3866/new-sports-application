package com.sportsapp.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * 외부에서 들어온 내부 신원 헤더를 무조건 폐기한다 (W1-06b §6-3 스푸핑 방어 ②).
 *
 * 인증 필터보다 **앞**에 등록된다. 인증을 거치지 않는 경로(permitAll·비인증 조회)에도 위조 헤더가
 * 남지 않아야 하므로, 인증 여부와 무관하게 모든 요청을 폐기 래퍼로 감싼다.
 *
 * 위조 시도 자체로 요청을 거부하지 않는다 — 정상 클라이언트가 우연히 같은 이름을 쓰는 경우까지
 * 401 로 만들면 가용성 문제가 되고, 폐기만으로 목적(신뢰 경계 보호)은 이미 달성된다.
 */
@Component
class InternalIdentityHeaderSanitizingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        filterChain.doFilter(InternalIdentityHeaderRequest(request, identity = null), response)
    }
}
