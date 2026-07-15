package com.sportsapp.presentation.support

import com.sportsapp.domain.user.vo.UserPrincipal
import org.springframework.core.MethodParameter
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * AUTH-04 — standalone MockMvc에서 `@AuthenticationPrincipal UserPrincipal`(non-null) /
 * `UserPrincipal?`(nullable) 파라미터를 고정 사용자로 해석하는 공용 리졸버.
 *
 * 실제 Spring Security 필터체인 없이 컨트롤러 로직만 검증할 때 쓴다(CommunityApiControllerTest 선례).
 * `userId`가 null이면 `UserPrincipal?`(익명 요청 시뮬레이션)로만 해석되고, non-null 파라미터
 * 타입에는 값을 채울 수 없으므로 그런 조합은 테스트 설계 오류다.
 */
fun fixedPrincipalResolver(
    userId: Long?,
    email: String = "test@sportsapp.local",
    roles: List<String> = listOf("USER"),
): HandlerMethodArgumentResolver = object : HandlerMethodArgumentResolver {
    override fun supportsParameter(parameter: MethodParameter): Boolean =
        parameter.hasParameterAnnotation(AuthenticationPrincipal::class.java) &&
            parameter.parameterType == UserPrincipal::class.java

    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?,
    ): Any? = userId?.let { UserPrincipal(id = it, email = email, roles = roles) }
}

/**
 * AUTH-04 — `HandlerInterceptor`(예: `EntryTokenGateInterceptor`)처럼 `@AuthenticationPrincipal`
 * 파라미터 리졸버가 아니라 `SecurityContextHolder`를 직접 읽는 컴포넌트를 standalone MockMvc에서
 * 검증할 때 쓴다. 실제 배포에서는 `JwtAuthenticationFilter`가 컨텍스트를 채우지만, standalone
 * MockMvc는 서블릿 필터 체인을 실행하지 않으므로 테스트가 직접 채운다.
 */
fun withAuthenticatedPrincipal(
    userId: Long,
    email: String = "test@sportsapp.local",
    roles: List<String> = listOf("USER"),
    block: () -> Unit,
) {
    val principal = UserPrincipal(id = userId, email = email, roles = roles)
    val authentication = UsernamePasswordAuthenticationToken(
        principal,
        null,
        roles.map { role -> SimpleGrantedAuthority("ROLE_$role") },
    )
    SecurityContextHolder.getContext().authentication = authentication
    try {
        block()
    } finally {
        SecurityContextHolder.clearContext()
    }
}
