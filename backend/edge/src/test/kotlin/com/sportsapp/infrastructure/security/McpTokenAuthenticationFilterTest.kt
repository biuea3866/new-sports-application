package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.AuthenticatedPrincipal
import com.sportsapp.domain.identity.gateway.PlatformMcpIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.McpIdentityVerification
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

/** edge 는 구체 주체 타입(bootstrap 의 `McpUserPrincipal`)을 알 수 없으므로 테스트용 대역을 쓴다. */
private data class StubMcpPrincipal(val userId: Long) : AuthenticatedPrincipal

class McpTokenAuthenticationFilterTest : BehaviorSpec({

    val verificationGateway = mockk<PlatformMcpIdentityVerificationGateway>()
    val filter = McpTokenAuthenticationFilter(verificationGateway)

    fun requestWith(authorizationHeader: String?, forgeInternalHeaders: Boolean = false): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            requestURI = "/mcp/tools/list"
            authorizationHeader?.let { addHeader("Authorization", it) }
            if (forgeInternalHeaders) {
                addHeader(InternalIdentityHeaders.SUBJECT, "999")
                addHeader(InternalIdentityHeaders.SCOPES, "write:booking:any")
            }
        }

    beforeEach {
        SecurityContextHolder.clearContext()
        clearMocks(verificationGateway, answers = false)
    }

    afterEach { SecurityContextHolder.clearContext() }

    Given("유효한 MCP 토큰으로 요청이 들어오면") {
        val plainToken = "mcp_1_validrandomsecretstring1234567890"
        val principal = StubMcpPrincipal(userId = 10L)

        beforeEach {
            every { verificationGateway.verify(plainToken) } returns McpIdentityVerification.valid(
                principal = principal,
                authorities = listOf("MCP_SCOPE_READ_FACILITY", "ROLE_MCP_TOKEN"),
                subjectId = 10L,
                scopes = listOf("read:facility"),
            )
            justRun { verificationGateway.recordUsage(10L) }
        }

        When("필터를 통과하면") {
            Then("검증된 주체와 권한이 SecurityContext 에 주입된다") {
                var captured: Authentication? = null
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    captured = SecurityContextHolder.getContext().authentication
                }

                filter.doFilter(requestWith("Bearer $plainToken"), MockHttpServletResponse(), filterChain)

                captured.shouldNotBeNull()
                requireNotNull(captured).principal shouldBe principal
                requireNotNull(captured).authorities.map { it.authority } shouldBe
                    listOf("MCP_SCOPE_READ_FACILITY", "ROLE_MCP_TOKEN")
            }

            Then("검증된 신원이 내부 헤더로 전파된다") {
                var forwarded: HttpServletRequest? = null
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    forwarded = firstArg<HttpServletRequest>()
                }

                filter.doFilter(requestWith("Bearer $plainToken"), MockHttpServletResponse(), filterChain)

                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SUBJECT) shouldBe "10"
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.CHANNEL) shouldBe "MCP_TOKEN"
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SCOPES) shouldBe "read:facility"
            }

            Then("토큰 사용 기록이 남는다") {
                filter.doFilter(
                    requestWith("Bearer $plainToken"),
                    MockHttpServletResponse(),
                    mockk<FilterChain>(relaxed = true),
                )

                verify(exactly = 1) { verificationGateway.recordUsage(10L) }
            }
        }
    }

    Given("외부에서 내부 신원 헤더를 위조하고 유효한 토큰을 함께 보내면") {
        val plainToken = "mcp_1_validrandomsecretstring1234567890"

        beforeEach {
            every { verificationGateway.verify(plainToken) } returns McpIdentityVerification.valid(
                principal = StubMcpPrincipal(userId = 10L),
                authorities = listOf("ROLE_MCP_TOKEN"),
                subjectId = 10L,
                scopes = emptyList(),
            )
            justRun { verificationGateway.recordUsage(10L) }
        }

        When("필터를 통과하면") {
            Then("위조된 값이 아니라 검증 결과가 전파된다 — 스코프 상승이 막힌다") {
                var forwarded: HttpServletRequest? = null
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    forwarded = firstArg<HttpServletRequest>()
                }

                filter.doFilter(
                    requestWith("Bearer $plainToken", forgeInternalHeaders = true),
                    MockHttpServletResponse(),
                    filterChain,
                )

                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SUBJECT) shouldBe "10"
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SCOPES) shouldBe ""
            }
        }
    }

    Given("무효한 MCP 토큰으로 요청이 들어오면") {
        val plainToken = "mcp_999_nonexistent"

        beforeEach {
            every { verificationGateway.verify(plainToken) } returns McpIdentityVerification.invalid()
        }

        When("필터를 통과하면") {
            Then("401 로 거부되고 downstream 으로 넘어가지 않는다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>(relaxed = true)

                filter.doFilter(requestWith("Bearer $plainToken"), response, filterChain)

                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
                verify(exactly = 0) { verificationGateway.recordUsage(any()) }
            }
        }
    }

    Given("무효한 토큰과 함께 내부 헤더를 위조해 보내면") {
        val plainToken = "mcp_999_nonexistent"

        beforeEach {
            every { verificationGateway.verify(plainToken) } returns McpIdentityVerification.invalid()
        }

        When("필터를 통과하면") {
            Then("401 로 끝나고 위조된 신원이 downstream 에 도달하지 않는다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>(relaxed = true)

                filter.doFilter(
                    requestWith("Bearer $plainToken", forgeInternalHeaders = true),
                    response,
                    filterChain,
                )

                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("MCP 자격증명이 아닌 요청이면") {
        listOf(
            "Authorization 헤더 없음" to null,
            "Bearer 형식이 아님" to "Basic dXNlcjpwYXNz",
            "mcp_ prefix 없음 (JWT 등)" to "Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig",
            "mcp_ 뒤 id 가 숫자가 아님" to "Bearer mcp_abc_secret",
            "mcp_ 뒤 구분자가 없음" to "Bearer mcp_1",
        ).forEach { (caseName, header) ->
            When(caseName) {
                Then("검증을 시도하지 않고 downstream 으로 통과시킨다") {
                    val request = requestWith(header)
                    val response = MockHttpServletResponse()
                    val filterChain = mockk<FilterChain>(relaxed = true)

                    filter.doFilter(request, response, filterChain)

                    SecurityContextHolder.getContext().authentication.shouldBeNull()
                    verify(exactly = 1) { filterChain.doFilter(request, response) }
                    verify(exactly = 0) { verificationGateway.verify(any()) }
                }
            }
        }
    }
})
