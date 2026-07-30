package com.sportsapp.testkit.presentation.support

import com.sportsapp.domain.common.security.UserPrincipal
import org.springframework.core.MethodParameter
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer

/**
 * [W1-01b 리뷰 ①] AUTH-04 — standalone MockMvc에서 `@AuthenticationPrincipal UserPrincipal`(non-null) /
 * `UserPrincipal?`(nullable) 파라미터를 고정 사용자로 해석하는 공용 리졸버. payment·commerce·
 * facility-booking 3모듈 복제본을 `common`의 testFixtures 로 통합한 버전이다.
 *
 * 실제 Spring Security 필터체인 없이 컨트롤러 로직만 검증할 때 쓴다(CommunityApiControllerTest 선례).
 * `userId`가 null이면 `UserPrincipal?`(익명 요청 시뮬레이션)로만 해석되고, non-null 파라미터
 * 타입에는 값을 채울 수 없으므로 그런 조합은 테스트 설계 오류다.
 *
 * bootstrap 의 `com.sportsapp.presentation.support.PrincipalArgumentResolver`(AUTH-04)와 동일 계약이다.
 * bootstrap 은 `SecurityContextHolder`를 직접 읽는 컴포넌트 검증용 `withAuthenticatedPrincipal`도
 * 함께 갖고 있으나, payment·commerce·facility-booking 은 이를 쓰지 않아 이 공용 버전에는 포함하지 않는다.
 *
 * 패키지를 `com.sportsapp.testkit.presentation.support`로 분리한 이유는
 * `com.sportsapp.testkit.presentation.exception.GlobalExceptionHandler` KDoc 참고 — bootstrap 자신의
 * `com.sportsapp.presentation.support.PrincipalArgumentResolver` 원본과 FQCN 충돌(클래스패스 섀도잉)을
 * 피하기 위함이다.
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
