package com.sportsapp.infrastructure.security

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
        auth.requestMatchers("/admin/**").hasRole("ADMIN")
        auth.requestMatchers("/api/facility-owner/**").authenticated()
        auth.requestMatchers("/api/event-host/**").authenticated()
        auth.requestMatchers("/api/goods-seller/**").authenticated()
        auth.requestMatchers("/api/operator/**").authenticated()
        auth.requestMatchers("/api/admin/mcp/tokens/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/mcp/audit-logs/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/mcp/usage-analytics/**").hasRole("ADMIN")
        auth.requestMatchers("/api/admin/partners/**").hasRole("ADMIN")
        auth.requestMatchers("/mcp/**").authenticated()
        // TODO(AUTH-04): SecurityContext 통합 전까지 도메인 API는 X-User-Id 헤더 기반으로 임시 permitAll
        auth.requestMatchers(HttpMethod.POST, "/images/presigned-upload").authenticated()
        auth.requestMatchers(
            "/bookings/**", "/payments/**", "/facilities/**",
            "/products/**", "/posts/**", "/comments/**", "/rooms/**",
            "/events/**", "/notifications/**",
            "/cart/**", "/ticket-orders/**", "/goods-orders/**",
            "/weather/**",
            "/operator/inbox/**", // TODO(AUTH-04): JWT 인증 통합 시 제거
            "/limited-drops/**", // TODO(AUTH-04): JWT 인증 통합 시 제거 — goods-orders와 동일한 X-User-Id 헤더 임시 방식
        ).permitAll()
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
