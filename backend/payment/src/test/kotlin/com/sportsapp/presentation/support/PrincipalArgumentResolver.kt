package com.sportsapp.presentation.support

import com.sportsapp.domain.common.security.UserPrincipal
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
 * [W1-01b] payment 소유 standalone MockMvc 컨트롤러 테스트(PaymentApiControllerTest) 전용 로컬 복제본.
 * bootstrap 의 `com.sportsapp.presentation.support.PrincipalArgumentResolver`(AUTH-04)와 동일 계약이다.
 * `domain.common.security.UserPrincipal`(common 모듈) 외 다른 모듈 의존이 없어 그대로 복제 가능하다 —
 * 배경은 commerce·facility-booking 의 GlobalExceptionHandler.kt KDoc 참고(test 소스셋은 모듈 간
 * 공유되지 않아 split-package 제약 대상이 아니다).
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
