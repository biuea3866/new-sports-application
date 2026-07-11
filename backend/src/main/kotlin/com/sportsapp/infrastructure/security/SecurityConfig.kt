package com.sportsapp.infrastructure.security

import com.sportsapp.infrastructure.loadshedding.LoadSheddingFilter
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val partnerApiKeyAuthenticationFilter: PartnerApiKeyAuthenticationFilter,
    private val loadSheddingFilter: LoadSheddingFilter,
) {
    @Autowired(required = false)
    private var mcpTokenAuthenticationFilter: McpTokenAuthenticationFilter? = null

    @Value("\${partner.auth.enabled:false}")
    private var partnerAuthEnabled: Boolean = false

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .cors { it.configurationSource(corsConfigurationSource()) }
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(jsonAuthenticationEntryPoint())
                it.accessDeniedHandler(jsonAccessDeniedHandler())
            }
            .authorizeHttpRequests { configureAuthorization(it) }
            // F2: 부하 셰딩은 인증 연산(JWT 파싱 등) 이전에 거부하도록 필터 체인 최전방에 둔다.
            .addFilterBefore(loadSheddingFilter, UsernamePasswordAuthenticationFilter::class.java)
            .also { config ->
                mcpTokenAuthenticationFilter?.let {
                    config.addFilterBefore(it, UsernamePasswordAuthenticationFilter::class.java)
                }
            }
            .also { config ->
                if (partnerAuthEnabled) {
                    config.addFilterBefore(partnerApiKeyAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
                }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
    }

    private fun configureAuthorization(
        auth: AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry,
    ) {
        auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/users/register").permitAll()
        auth.requestMatchers("/actuator/health", "/actuator/info").permitAll()
        // BE-10: 규칙 엔진(Grafana)·내부 raise 진입점. 컨트롤러가 grafana=Authorization Bearer,
        // 내부raise=X-Alert-Token으로 공유 시크릿을 자체 검증하므로 Spring Security 레벨에서는 permitAll.
        auth.requestMatchers("/internal/alerts/**").permitAll()
        auth.requestMatchers("/admin/**").hasRole("ADMIN")
        // BE-10: 데모 게이팅(FR-9)은 X-User-Id 헤더 기반 평가라 permitAll (AUTH-04 관례)
        auth.requestMatchers("/feature-demo/**").permitAll()
        auth.requestMatchers("/api/facility-owner/**").authenticated()
        auth.requestMatchers("/api/event-host/**").authenticated()
        auth.requestMatchers("/api/goods-seller/**").authenticated()
        auth.requestMatchers("/api/operator/**").authenticated()
        auth.requestMatchers("/api/admin/mcp/tokens/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/mcp/audit-logs/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/mcp/usage-analytics/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/partners/**").hasRole("ADMIN")
        auth.requestMatchers("/mcp/**").authenticated()
        // BE-04: WebSocket handshake 는 StompAuthChannelInterceptor 가 CONNECT 시 JWT 를 자체 검증하므로
        // Spring Security 레벨에서는 permitAll. /communities/** 컨트롤러는 BE-08(wave3)에서 추가되며,
        // 규칙 선등록은 무해하다.
        auth.requestMatchers("/ws/**").permitAll()
        auth.requestMatchers("/communities/**").authenticated()
        // BE-12: /rooms/** 는 RoomApiController/MessageApiController 가 X-User-Id 임시 헤더에서
        // Authorization: Bearer JWT(@AuthenticationPrincipal UserPrincipal) 로 전환됨에 따라 승격.
        auth.requestMatchers("/rooms/**").authenticated()
        // BE-11: 거래 채팅 생성은 buyerId(principal.id) 식별이 필요해, 아래 `/products/**` permitAll 보다
        // 먼저 매칭되도록 authenticated()를 선언한다 (ProductChatApiController).
        auth.requestMatchers(HttpMethod.POST, "/products/*/chat").authenticated()
        // TODO(AUTH-04): SecurityContext 통합 전까지 도메인 API는 X-User-Id 헤더 기반으로 임시 permitAll
        auth.requestMatchers(HttpMethod.POST, "/images/presigned-upload").authenticated()
        auth.requestMatchers(
            "/bookings/**", "/payments/**", "/facilities/**",
            "/products/**", "/posts/**", "/comments/**",
            "/events/**", "/notifications/**",
            "/cart/**", "/ticket-orders/**", "/goods-orders/**",
            "/weather/**",
            "/operator/inbox/**", // TODO(AUTH-04): JWT 인증 통합 시 제거
            "/limited-drops/**", // TODO(AUTH-04): JWT 인증 통합 시 제거 — goods-orders와 동일한 X-User-Id 헤더 임시 방식
            "/virtual-queues/**", // BE-08: /limited-drops/**와 동일한 X-User-Id 헤더 임시 관례(TODO AUTH-04 제거 대상)
        ).permitAll()
        // BE-09: catalog(BE-07)·order(BE-08) 통합 파사드 인가 등록. catalog는 비로그인 통합검색을
        // 허용(FR-1/2), order는 principal.id(JWT) 기반 조회라 인증 필수(FR-5, User Scenario 7).
        auth.requestMatchers("/api/catalog/**").permitAll()
        auth.requestMatchers("/api/orders/**").authenticated()
        auth.anyRequest().authenticated()
    }

    private fun jsonAuthenticationEntryPoint(): AuthenticationEntryPoint =
        AuthenticationEntryPoint { _: HttpServletRequest, response: HttpServletResponse, _: AuthenticationException ->
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"status":401,"title":"Unauthorized","detail":"Authentication required"}""")
        }

    private fun jsonAccessDeniedHandler(): AccessDeniedHandler =
        AccessDeniedHandler { _: HttpServletRequest, response: HttpServletResponse, _: AccessDeniedException ->
            response.status = HttpServletResponse.SC_FORBIDDEN
            response.contentType = MediaType.APPLICATION_JSON_VALUE
            response.writer.write("""{"status":403,"title":"Forbidden","detail":"Access denied"}""")
        }

    private fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration().apply {
            allowedOriginPatterns = listOf("http://localhost:*", "http://127.0.0.1:*")
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", configuration)
        }
    }
}
