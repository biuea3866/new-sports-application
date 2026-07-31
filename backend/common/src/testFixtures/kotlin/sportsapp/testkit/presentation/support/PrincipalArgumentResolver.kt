package sportsapp.testkit.presentation.support

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
 * [W1-01b 리뷰 ①] AUTH-04 — standalone MockMvc에서 `@AuthenticationPrincipal UserPrincipal`(non-null) /
 * `UserPrincipal?`(nullable) 파라미터를 고정 사용자로 해석하는 공용 리졸버. payment·commerce·
 * facility-booking 3모듈 복제본을 `common`의 testFixtures 로 통합한 버전이다.
 *
 * 실제 Spring Security 필터체인 없이 컨트롤러 로직만 검증할 때 쓴다(CommunityApiControllerTest 선례).
 * `userId`가 null이면 `UserPrincipal?`(익명 요청 시뮬레이션)로만 해석되고, non-null 파라미터
 * 타입에는 값을 채울 수 없으므로 그런 조합은 테스트 설계 오류다.
 *
 * bootstrap 의 `com.sportsapp.presentation.support.PrincipalArgumentResolver`(AUTH-04) 원본과 동일
 * 계약이며, W1-01d 에서 그 원본의 마지막 소비자가 social·edge 로 이관돼 원본은 삭제됐다 — 지금은
 * 이 파일이 유일한 정의다.
 *
 * 패키지를 `sportsapp.testkit.presentation.support`(`com.sportsapp` 밖)로 분리한 이유는
 * `sportsapp.testkit.presentation.exception.GlobalExceptionHandler` KDoc 참고 — bootstrap 컴포넌트
 * 스캔 오염을 차단하기 위함이다.
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
 * [W1-01d] AUTH-04 — `HandlerInterceptor`(예: `EntryTokenGateInterceptor`)처럼 `@AuthenticationPrincipal`
 * 파라미터 리졸버가 아니라 `SecurityContextHolder`를 직접 읽는 컴포넌트를 standalone MockMvc에서
 * 검증할 때 쓴다. 실제 배포에서는 `JwtAuthenticationFilter`가 컨텍스트를 채우지만, standalone
 * MockMvc는 서블릿 필터 체인을 실행하지 않으므로 테스트가 직접 채운다.
 *
 * bootstrap 의 `com.sportsapp.presentation.support.PrincipalArgumentResolver#withAuthenticatedPrincipal`
 * (AUTH-04)에서 이관한 함수다 — edge 모듈의 `EntryTokenGateInterceptorTest`가 이 함수를 필요로 해
 * `fixedPrincipalResolver`와 함께 공용 버전으로 승격했고, 이관으로 소비자가 사라진 bootstrap 원본은
 * 같은 티켓에서 삭제했다(`@Component`가 아닌 순수 함수라 rule "common/testFixtures 에 @Component
 * 계열을 추가하지 마라" 예외 대상이 아니다).
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
