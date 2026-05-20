package com.sportsapp.infrastructure.security

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.AuthenticationEntryPoint
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.access.AccessDeniedHandler
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .exceptionHandling {
                it.authenticationEntryPoint(jsonAuthenticationEntryPoint())
                it.accessDeniedHandler(jsonAccessDeniedHandler())
            }
            .authorizeHttpRequests { auth ->
                auth.requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/users/register").permitAll()
                auth.requestMatchers("/actuator/health", "/actuator/info").permitAll()
                auth.requestMatchers("/admin/**").hasRole("ADMIN")
                // TODO(AUTH-04): SecurityContext 통합 전까지 도메인 API는 X-User-Id 헤더 기반으로 임시 permitAll
                auth.requestMatchers(
                    "/bookings/**", "/payments/**", "/facilities/**",
                    "/products/**", "/posts/**", "/comments/**", "/rooms/**",
                    "/events/**", "/notifications/**",
                    "/cart/**", "/ticket-orders/**", "/goods-orders/**",
                ).permitAll()
                auth.anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)
        return http.build()
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

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
