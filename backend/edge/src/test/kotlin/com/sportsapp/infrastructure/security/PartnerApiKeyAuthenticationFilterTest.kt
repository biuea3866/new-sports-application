package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.UserPrincipal
import com.sportsapp.domain.identity.gateway.PlatformPartnerIdentityVerificationGateway
import com.sportsapp.domain.identity.vo.PartnerApiCallActivity
import com.sportsapp.domain.identity.vo.PartnerIdentityVerification
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder

class PartnerApiKeyAuthenticationFilterTest : BehaviorSpec({

    val verificationGateway = mockk<PlatformPartnerIdentityVerificationGateway>()
    val filter = PartnerApiKeyAuthenticationFilter(verificationGateway)

    val plainKey = "partner_1_validrandomsecretstring1234567890"
    val linkedUserPrincipal = UserPrincipal(
        id = 10L,
        email = "partner-10@sportsapp.com",
        roles = listOf("USER", "GOODS_SELLER"),
        partnerAuthenticated = true,
    )

    fun requestWith(authorizationHeader: String?, forgeInternalHeaders: Boolean = false): MockHttpServletRequest =
        MockHttpServletRequest().apply {
            method = "POST"
            requestURI = "/goods-orders"
            remoteAddr = "10.0.0.7"
            addHeader("User-Agent", "partner-sdk/1.0")
            authorizationHeader?.let { addHeader("Authorization", it) }
            if (forgeInternalHeaders) addHeader(InternalIdentityHeaders.SUBJECT, "999")
        }

    beforeEach {
        SecurityContextHolder.clearContext()
        clearMocks(verificationGateway, answers = false)
    }

    afterEach { SecurityContextHolder.clearContext() }

    Given("유효한 파트너 API 키로 요청이 들어오면") {
        beforeEach {
            every { verificationGateway.verify(plainKey) } returns PartnerIdentityVerification.valid(
                principal = linkedUserPrincipal,
                authorities = listOf("ROLE_USER", "ROLE_GOODS_SELLER"),
                partnerId = 1L,
                linkedUserId = 10L,
            )
            justRun { verificationGateway.recordUsage(any()) }
            justRun { verificationGateway.recordActivity(any()) }
        }

        When("필터를 통과하면") {
            Then("연동 유저 주체가 SecurityContext 에 주입된다") {
                var captured: Authentication? = null
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    captured = SecurityContextHolder.getContext().authentication
                }

                filter.doFilter(requestWith("Bearer $plainKey"), MockHttpServletResponse(), filterChain)

                captured.shouldNotBeNull()
                requireNotNull(captured).principal shouldBe linkedUserPrincipal
                requireNotNull(captured).authorities.map { it.authority } shouldBe
                    listOf("ROLE_USER", "ROLE_GOODS_SELLER")
            }

            Then("검증된 신원이 내부 헤더로 전파된다") {
                var forwarded: HttpServletRequest? = null
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    forwarded = firstArg<HttpServletRequest>()
                }

                filter.doFilter(requestWith("Bearer $plainKey"), MockHttpServletResponse(), filterChain)

                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SUBJECT) shouldBe "10"
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.CHANNEL) shouldBe "PARTNER_API_KEY"
            }

            Then("키 사용 기록이 파싱된 keyId 로 남는다") {
                filter.doFilter(
                    requestWith("Bearer $plainKey"),
                    MockHttpServletResponse(),
                    mockk<FilterChain>(relaxed = true),
                )

                verify(exactly = 1) { verificationGateway.recordUsage(1L) }
            }

            Then("응답이 끝난 뒤 호출 감사가 확정된 상태코드로 기록된다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>()
                every { filterChain.doFilter(any(), any()) } answers {
                    secondArg<MockHttpServletResponse>().status = 201
                }
                val activity = slot<PartnerApiCallActivity>()
                justRun { verificationGateway.recordActivity(capture(activity)) }

                filter.doFilter(requestWith("Bearer $plainKey"), response, filterChain)

                activity.captured.partnerId shouldBe 1L
                activity.captured.userId shouldBe 10L
                activity.captured.httpMethod shouldBe "POST"
                activity.captured.requestPath shouldBe "/goods-orders"
                activity.captured.statusCode shouldBe 201
                activity.captured.ipAddr shouldBe "10.0.0.7"
                activity.captured.userAgent shouldBe "partner-sdk/1.0"
            }
        }
    }

    Given("정지된 파트너의 API 키로 요청이 들어오면") {
        beforeEach {
            every { verificationGateway.verify(plainKey) } returns PartnerIdentityVerification.suspended()
        }

        When("필터를 통과하면") {
            Then("403 으로 거부되고 downstream 으로 넘어가지 않는다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>(relaxed = true)

                filter.doFilter(requestWith("Bearer $plainKey"), response, filterChain)

                response.status shouldBe 403
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
                verify(exactly = 0) { verificationGateway.recordUsage(any()) }
                verify(exactly = 0) { verificationGateway.recordActivity(any()) }
            }
        }
    }

    Given("무효한 파트너 API 키로 요청이 들어오면") {
        beforeEach {
            every { verificationGateway.verify(plainKey) } returns PartnerIdentityVerification.invalid()
        }

        When("필터를 통과하면") {
            Then("401 로 거부된다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>(relaxed = true)

                filter.doFilter(requestWith("Bearer $plainKey"), response, filterChain)

                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("무효한 키와 함께 내부 헤더를 위조해 보내면") {
        beforeEach {
            every { verificationGateway.verify(plainKey) } returns PartnerIdentityVerification.invalid()
        }

        When("필터를 통과하면") {
            Then("401 로 끝나고 위조된 신원이 downstream 에 도달하지 않는다") {
                val response = MockHttpServletResponse()
                val filterChain = mockk<FilterChain>(relaxed = true)

                filter.doFilter(
                    requestWith("Bearer $plainKey", forgeInternalHeaders = true),
                    response,
                    filterChain,
                )

                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("파트너 자격증명이 아닌 요청이면") {
        listOf(
            "Authorization 헤더 없음" to null,
            "Bearer 형식이 아님" to "Basic dXNlcjpwYXNz",
            "partner_ prefix 없음 (JWT 등)" to "Bearer eyJhbGciOiJSUzI1NiJ9.payload.sig",
            "partner_ 뒤 id 가 숫자가 아님" to "Bearer partner_abc_secret",
            "partner_ 뒤 구분자가 없음" to "Bearer partner_1",
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
