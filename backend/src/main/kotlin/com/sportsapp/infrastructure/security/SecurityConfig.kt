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
        // BE-11: 거래 채팅 생성은 buyerId(principal.id) 식별이 필요해, 아래 products 공개 조회 규칙보다
        // 먼저 매칭되도록 authenticated()를 선언한다 (ProductChatApiController).
        auth.requestMatchers(HttpMethod.POST, "/products/*/chat").authenticated()
        auth.requestMatchers(HttpMethod.POST, "/images/presigned-upload").authenticated()

        // AUTH-04: 도메인 API를 X-User-Id 헤더 → JWT(@AuthenticationPrincipal UserPrincipal) 전환.
        // 공개 브라우징 목적 GET은 permitAll 유지, 작성·개인화 조회·결제는 authenticated().
        // PG 콜백(웹훅)은 JWT를 실을 수 없는 외부 시스템 호출이라 별도 permitAll — 컨트롤러 자체에
        // 서명 검증 로직이 없는 기존 동작을 그대로 보존한다(PaymentWebhookApiController).
        auth.requestMatchers(HttpMethod.POST, "/payments/webhook").permitAll()
        auth.requestMatchers("/payments/**").authenticated() // 결제 생성·조회 전부 개인 데이터

        auth.requestMatchers("/bookings/**").authenticated() // 예약 생성·조회 전부 개인 데이터

        // 시설/슬롯/시설상품 목록·상세는 비로그인 브라우징 허용, 등록·변경(슬롯·시설상품)은 시설주만
        auth.requestMatchers(HttpMethod.GET, "/facilities/**").permitAll()
        auth.requestMatchers("/facilities/**").authenticated()

        // 게시글 검색·상세는 공개 조회(선택적 principal), 작성은 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/posts/**").permitAll()
        auth.requestMatchers("/posts/**").authenticated()

        // 댓글 목록은 게시글과 함께 공개 조회, 작성·삭제는 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/comments/**").permitAll()
        auth.requestMatchers("/comments/**").authenticated()

        // 이벤트 목록·상세는 공개 조회, 좌석 선점/해제는 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/events/**").permitAll()
        auth.requestMatchers("/events/**").authenticated()

        auth.requestMatchers("/notifications/**").authenticated() // 전부 개인 알림 데이터
        auth.requestMatchers("/cart/**").authenticated() // 전부 개인 장바구니 데이터
        auth.requestMatchers("/ticket-orders/**").authenticated() // 주문 생성·조회 전부 개인 데이터
        auth.requestMatchers("/goods-orders/**").authenticated() // 전부 개인 주문 데이터
        auth.requestMatchers("/operator/inbox/**").authenticated() // 운영자 개인 인박스

        // 한정판 상세·통계는 공개 조회, 개설·구매는 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/limited-drops/**").permitAll()
        auth.requestMatchers("/limited-drops/**").authenticated()

        // 대기열 운영 통계(GET stats)는 기존 동작 보존(공개 유지), 입장/조회/이탈은 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/virtual-queues/*/*/stats").permitAll()
        auth.requestMatchers("/virtual-queues/**").authenticated()

        // 모집 목록·상세는 컨트롤러가 nullable principal로 비로그인 조회를 설계해 공개,
        // 신청자 목록·개설·신청·취소는 로그인 필요
        auth.requestMatchers(HttpMethod.GET, "/recruitments", "/recruitments/*").permitAll()
        auth.requestMatchers("/recruitments/**").authenticated()
        auth.requestMatchers("/applications/**").authenticated() // 본인 신청 내역 전부 개인 데이터

        auth.requestMatchers("/products/**").permitAll() // 상품 검색·상세 공개 조회 (POST 채팅 예외는 위에서 선매칭)
        auth.requestMatchers("/weather/**").permitAll() // 공개 날씨 데이터

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
