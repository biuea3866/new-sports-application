package com.sportsapp.presentation.mcp.security

import com.sportsapp.domain.mcp.McpScope
import com.sportsapp.domain.mcp.McpToken
import com.sportsapp.domain.mcp.McpTokenRepository
import com.sportsapp.domain.mcp.McpTokenScopeRepository
import com.sportsapp.domain.mcp.McpTokenStatus
import com.sportsapp.domain.common.Permission
import com.sportsapp.domain.common.PermissionRepository
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.crypto.password.PasswordEncoder
import java.time.ZonedDateTime

class McpTokenAuthenticationFilterTest : BehaviorSpec({

    val mcpTokenRepository = mockk<McpTokenRepository>()
    val mcpTokenScopeRepository = mockk<McpTokenScopeRepository>()
    val permissionRepository = mockk<PermissionRepository>()
    val passwordEncoder = mockk<PasswordEncoder>()

    val filter = McpTokenAuthenticationFilter(
        mcpTokenRepository = mcpTokenRepository,
        mcpTokenScopeRepository = mcpTokenScopeRepository,
        permissionRepository = permissionRepository,
        passwordEncoder = passwordEncoder,
    )

    fun makeToken(
        id: Long = 1L,
        userId: Long = 10L,
        status: McpTokenStatus = McpTokenStatus.ACTIVE,
        expiresAt: ZonedDateTime? = null,
    ): McpToken {
        val token = McpToken.create(
            userId = userId,
            name = "test-token",
            tokenHash = "bcrypt-hashed",
            expiresAt = expiresAt,
        )
        val idField = McpToken::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(token, id)
        val superclass = token.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(token, ZonedDateTime.now())
        }
        if (status == McpTokenStatus.SUSPENDED) token.suspend()
        if (status == McpTokenStatus.REVOKED) token.revoke()
        return token
    }

    fun makePermission(name: String, id: Long = 100L): Permission {
        val permission = Permission(name = name)
        val idField = Permission::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(permission, id)
        val superclass = permission.javaClass.superclass
        listOf("createdAt", "updatedAt").forEach { fieldName ->
            val field = superclass.getDeclaredField(fieldName)
            field.isAccessible = true
            field.set(permission, ZonedDateTime.now())
        }
        return permission
    }

    beforeEach {
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_INHERITABLETHREADLOCAL)
        SecurityContextHolder.clearContext()
        clearMocks(mcpTokenRepository, mcpTokenScopeRepository, permissionRepository, passwordEncoder)
    }

    afterEach {
        SecurityContextHolder.clearContext()
        SecurityContextHolder.setStrategyName(SecurityContextHolder.MODE_THREADLOCAL)
    }

    Given("[U-01] 유효한 Bearer 토큰으로 /mcp/** 요청이 들어오면") {
        val plainToken = "mcp_1_validrandomsecretstring1234567890"
        val token = makeToken(id = 1L, userId = 10L)
        val permission = makePermission("mcp.facility.read.own", 100L)
        val scope = com.sportsapp.domain.mcp.McpTokenScope.create(1L, 100L).also { s ->
            val superclass = s.javaClass.superclass
            listOf("createdAt", "updatedAt").forEach { fieldName ->
                val field = superclass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(s, ZonedDateTime.now())
            }
        }

        every { mcpTokenRepository.findById(1L) } returns token
        every { passwordEncoder.matches(plainToken, "bcrypt-hashed") } returns true
        every { mcpTokenScopeRepository.findByTokenId(1L) } returns listOf(scope)
        every { permissionRepository.findById(100L) } returns permission

        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        // capture authentication set inside filter during doFilter
        var capturedAuthentication: org.springframework.security.core.Authentication? = null
        every { filterChain.doFilter(any(), any()) } answers {
            capturedAuthentication = SecurityContextHolder.getContext().authentication
        }

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-01] SecurityContext에 McpUserPrincipal이 주입되고 chain.doFilter가 호출된다") {
                capturedAuthentication.shouldNotBeNull()
                val principal = requireNotNull(capturedAuthentication).principal as McpUserPrincipal
                principal.tokenId shouldBe 1L
                principal.userId shouldBe 10L
                verify(exactly = 1) { filterChain.doFilter(request, response) }
            }
        }
    }

    Given("[U-02] Authorization 헤더가 없는 요청이면") {
        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-02] SecurityContext에 principal이 없고 chain.doFilter는 호출된다") {
                SecurityContextHolder.getContext().authentication.shouldBeNull()
                verify(exactly = 1) { filterChain.doFilter(request, response) }
            }
        }
    }

    Given("[U-03] Bearer 형식이 아닌 Authorization 헤더면") {
        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Basic dXNlcjpwYXNz")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-03] SecurityContext에 principal이 없고 chain.doFilter는 호출된다") {
                SecurityContextHolder.getContext().authentication.shouldBeNull()
                verify(exactly = 1) { filterChain.doFilter(request, response) }
            }
        }
    }

    Given("[U-04] mcp_ prefix가 없는 토큰이면 (JWT 토큰 등 다른 형식)") {
        val plainToken = "invalid_token_format"
        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-04] McpTokenFilter는 처리하지 않고 downstream으로 통과시킨다") {
                verify(exactly = 1) { filterChain.doFilter(request, response) }
            }
        }
    }

    Given("[U-05] id는 유효하지만 DB에 존재하지 않는 토큰이면") {
        val plainToken = "mcp_999_nonexistent"
        every { mcpTokenRepository.findById(999L) } returns null

        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-05] 401 응답이 반환된다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("[U-06] bcrypt 검증에 실패하는 토큰이면") {
        val plainToken = "mcp_1_wrongsecret"
        val token = makeToken(id = 1L)

        every { mcpTokenRepository.findById(1L) } returns token
        every { passwordEncoder.matches(plainToken, "bcrypt-hashed") } returns false

        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-06] 401 응답이 반환된다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("[U-07] SUSPENDED 상태인 토큰이면") {
        val plainToken = "mcp_1_suspendedtoken1234567890"
        val token = makeToken(id = 1L, status = McpTokenStatus.SUSPENDED)

        every { mcpTokenRepository.findById(1L) } returns token
        every { passwordEncoder.matches(plainToken, "bcrypt-hashed") } returns true

        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-07] 401 응답이 반환된다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("[U-08] 만료된 토큰이면") {
        val plainToken = "mcp_1_expiredtoken1234567890"
        val expiredAt = ZonedDateTime.now().minusDays(1)
        val token = makeToken(id = 1L, expiresAt = expiredAt)

        every { mcpTokenRepository.findById(1L) } returns token
        every { passwordEncoder.matches(plainToken, "bcrypt-hashed") } returns true

        val request = MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            addHeader("Authorization", "Bearer $plainToken")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("[U-08] 401 응답이 반환된다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }
})
