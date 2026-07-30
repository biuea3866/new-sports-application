package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.user.gateway.JwtBlacklistStore
import com.sportsapp.domain.user.gateway.JwtIssuer
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/**
 * W1-05: RS256 전환 이후에도 [JwtAuthenticationFilter]·블랙리스트 무효화 계약이 그대로임을
 * 확인하는 회귀 테스트다. 필터 자체는 알고리즘을 모른다 — [JwtIssuer] 구현(RS256/HS256)에
 * 무관하게 jti 기준 블랙리스트 조회만 수행해야 한다.
 *
 * SecurityContext 는 `filterChain.doFilter` 모킹 콜백 안에서 캡처한다 — `filter.doFilter(...)` 호출과
 * 캡처가 같은 호출 스택(같은 스레드) 안에서 일어나므로 `SecurityContextHolder`의 기본 전략
 * (`MODE_THREADLOCAL`)만으로 충분하다(제거 후에도 GREEN 확인 — `McpTokenAuthenticationFilterTest`의
 * `MODE_INHERITABLETHREADLOCAL` 전환은 이 스펙에는 필요하지 않았다). `beforeEach`/`afterEach`는
 * 컨텍스트 격리만 담당한다.
 */
class JwtAuthenticationFilterTest : BehaviorSpec({

    beforeEach {
        SecurityContextHolder.clearContext()
    }
    afterEach {
        SecurityContextHolder.clearContext()
    }

    fun requestWithBearerToken(token: String): MockHttpServletRequest =
        MockHttpServletRequest().apply { addHeader("Authorization", "Bearer $token") }

    Given("RS256 으로 서명됐지만 블랙리스트에 등록된 토큰") {
        val jwtIssuer = mockk<JwtIssuer>()
        val jwtBlacklistStore = mockk<JwtBlacklistStore>()
        val filter = JwtAuthenticationFilter(jwtIssuer, jwtBlacklistStore)
        val token = "rs256-signed-but-blacklisted-token"

        every { jwtIssuer.validateToken(token) } returns true
        every { jwtIssuer.extractJti(token) } returns "blacklisted-jti"
        every { jwtBlacklistStore.isBlacklisted("blacklisted-jti") } returns true

        When("doFilter 를 호출하면") {
            val request = requestWithBearerToken(token)
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>(relaxed = true)
            var capturedAuthentication: Authentication? = null
            every { filterChain.doFilter(any(), any()) } answers {
                capturedAuthentication = SecurityContextHolder.getContext().authentication
            }

            filter.doFilter(request, response, filterChain)

            Then("RS256 으로 서명됐어도 블랙리스트 등록 토큰은 거부된다 — 로그아웃 무효화 회귀") {
                capturedAuthentication.shouldBeNull()
            }
        }
    }

    Given("RS256 으로 서명되고 블랙리스트에 없는 유효한 토큰") {
        val jwtIssuer = mockk<JwtIssuer>()
        val jwtBlacklistStore = mockk<JwtBlacklistStore>()
        val filter = JwtAuthenticationFilter(jwtIssuer, jwtBlacklistStore)
        val token = "rs256-signed-valid-token"

        every { jwtIssuer.validateToken(token) } returns true
        every { jwtIssuer.extractJti(token) } returns "valid-jti"
        every { jwtIssuer.extractUserId(token) } returns 42L
        every { jwtIssuer.extractEmail(token) } returns "user@example.com"
        every { jwtIssuer.extractRoles(token) } returns listOf("USER")
        every { jwtBlacklistStore.isBlacklisted("valid-jti") } returns false

        When("doFilter 를 호출하면") {
            val request = requestWithBearerToken(token)
            val response = MockHttpServletResponse()
            val filterChain = mockk<FilterChain>(relaxed = true)
            var capturedAuthentication: Authentication? = null
            every { filterChain.doFilter(any(), any()) } answers {
                capturedAuthentication = SecurityContextHolder.getContext().authentication
            }

            filter.doFilter(request, response, filterChain)

            Then("정상 인증된다 — RS256 전환 후에도 인증 흐름은 동일하다") {
                val principal = capturedAuthentication?.principal as? UserPrincipal
                requireNotNull(principal) { "principal 이 UserPrincipal 이어야 한다" }
                principal.id shouldBe 42L
                principal.email shouldBe "user@example.com"
                principal.roles shouldBe listOf("USER")
            }
        }
    }
})
