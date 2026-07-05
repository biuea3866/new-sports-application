package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.exceptions.UnauthorizedException
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.exception.PartnerApiKeyInactiveException
import com.sportsapp.domain.partner.exception.PartnerSuspendedException
import com.sportsapp.domain.partner.gateway.PartnerActivityRecorder
import com.sportsapp.domain.partner.gateway.PartnerApiKeyUsageRecorder
import com.sportsapp.domain.partner.service.AuthenticatedPartner
import com.sportsapp.domain.partner.service.PartnerDomainService
import com.sportsapp.domain.user.dto.UserWithRoles
import com.sportsapp.domain.user.entity.UserStatus
import com.sportsapp.domain.user.service.UserDomainService
import com.sportsapp.domain.user.vo.UserPrincipal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import jakarta.servlet.FilterChain
import java.time.ZonedDateTime
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder

class PartnerApiKeyAuthenticationFilterTest : BehaviorSpec({

    val partnerDomainService = mockk<PartnerDomainService>()
    val userDomainService = mockk<UserDomainService>()
    val partnerActivityRecorder = mockk<PartnerActivityRecorder>(relaxed = true)
    val partnerApiKeyUsageRecorder = mockk<PartnerApiKeyUsageRecorder>(relaxed = true)

    val filter = PartnerApiKeyAuthenticationFilter(
        partnerDomainService = partnerDomainService,
        userDomainService = userDomainService,
        partnerActivityRecorder = partnerActivityRecorder,
        partnerApiKeyUsageRecorder = partnerApiKeyUsageRecorder,
    )

    beforeEach {
        SecurityContextHolder.clearContext()
        clearMocks(partnerDomainService, userDomainService, partnerActivityRecorder, partnerApiKeyUsageRecorder, answers = false)
    }

    afterEach {
        SecurityContextHolder.clearContext()
    }

    Given("유효한 partner API Key로 요청이 들어오면") {
        val plainKey = "partner_1_validrandomsecretstring1234567890"
        every { partnerDomainService.authenticate(1L, plainKey) } returns
            AuthenticatedPartner(partnerId = 1L, linkedUserId = 10L)
        every { userDomainService.findByIdWithRoles(10L) } returns UserWithRoles(
            userId = 10L,
            email = "partner-10@sportsapp.com",
            status = UserStatus.ACTIVE,
            roleNames = listOf("GOODS_SELLER", "EVENT_HOST"),
            joinedAt = ZonedDateTime.now(),
        )

        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            method = "POST"
            addHeader("Authorization", "Bearer $plainKey")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        var capturedAuthentication: org.springframework.security.core.Authentication? = null
        every { filterChain.doFilter(any(), any()) } answers {
            capturedAuthentication = SecurityContextHolder.getContext().authentication
            response.status = 201
        }

        When("filter를 통과하면") {
            Then("연동 User principal(roles 포함)이 SecurityContext에 주입되고, 체인 진행 후 PartnerActivityRecorder.record가 응답 status로 호출된다") {
                filter.doFilter(request, response, filterChain)

                capturedAuthentication.shouldNotBeNull()
                val principal = requireNotNull(capturedAuthentication).principal as UserPrincipal
                principal.id shouldBe 10L
                principal.email shouldBe "partner-10@sportsapp.com"
                principal.roles shouldBe listOf("GOODS_SELLER", "EVENT_HOST")
                verify(exactly = 1) { filterChain.doFilter(request, response) }
                verify(exactly = 1) { userDomainService.findByIdWithRoles(10L) }

                val statusCodeSlot = slot<Int>()
                verify(exactly = 1) {
                    partnerActivityRecorder.record(
                        partnerId = 1L,
                        userId = 10L,
                        httpMethod = "POST",
                        requestPath = "/api/goods-seller/products",
                        statusCode = capture(statusCodeSlot),
                        latencyMs = any(),
                        ipAddr = any(),
                        userAgent = any(),
                        calledAt = any(),
                    )
                }
                statusCodeSlot.captured shouldBe 201
            }

            Then("PartnerApiKeyUsageRecorder.recordUsage가 인증된 keyId로 1회 호출된다") {
                filter.doFilter(request, response, filterChain)

                verify(exactly = 1) { partnerApiKeyUsageRecorder.recordUsage(1L) }
            }
        }
    }

    Given("partner_ prefix가 아닌 Authorization 헤더면") {
        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            addHeader("Authorization", "Bearer some-jwt-token")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("SecurityContext는 비어있고 체인은 그대로 진행된다(JWT 경로 무영향)") {
                SecurityContextHolder.getContext().authentication.shouldBeNull()
                verify(exactly = 1) { filterChain.doFilter(request, response) }
                verify(exactly = 0) { partnerDomainService.authenticate(any(), any()) }
            }
        }
    }

    Given("Authorization 헤더가 없으면") {
        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("체인은 그대로 진행된다") {
                SecurityContextHolder.getContext().authentication.shouldBeNull()
                verify(exactly = 1) { filterChain.doFilter(request, response) }
            }
        }
    }

    Given("REVOKED 상태의 API Key로 요청하면") {
        val plainKey = "partner_2_revokedkeysecret1234567890"
        every { partnerDomainService.authenticate(2L, plainKey) } throws
            PartnerApiKeyInactiveException(2L, ApiKeyStatus.REVOKED)

        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            addHeader("Authorization", "Bearer $plainKey")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("401 JSON을 반환하고 체인을 중단한다") {
                response.status shouldBe 401
                response.contentAsString shouldBe
                    """{"status":401,"title":"Unauthorized","detail":"Invalid or expired partner API key"}"""
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
                verify(exactly = 0) { partnerActivityRecorder.record(any(), any(), any(), any(), any(), any(), any(), any(), any()) }
                verify(exactly = 0) { partnerApiKeyUsageRecorder.recordUsage(any()) }
            }
        }
    }

    Given("SUSPENDED 상태의 파트너 API Key로 요청하면") {
        val plainKey = "partner_3_suspendedpartnersecret1234567890"
        every { partnerDomainService.authenticate(3L, plainKey) } throws
            PartnerSuspendedException(3L)

        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            addHeader("Authorization", "Bearer $plainKey")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("403을 반환하고 체인을 중단한다") {
                response.status shouldBe 403
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("hash가 일치하지 않는 API Key로 요청하면") {
        val plainKey = "partner_4_wrongsecret1234567890"
        every { partnerDomainService.authenticate(4L, plainKey) } throws
            UnauthorizedException("Invalid API key")

        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            addHeader("Authorization", "Bearer $plainKey")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("401을 반환하고 체인을 중단한다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }

    Given("id는 파싱되지만 DB에 존재하지 않는 API Key로 요청하면") {
        val plainKey = "partner_999_nonexistentsecret1234567890"
        every { partnerDomainService.authenticate(999L, plainKey) } throws
            UnauthorizedException("Invalid API key")

        val request = MockHttpServletRequest().apply {
            requestURI = "/api/goods-seller/products"
            addHeader("Authorization", "Bearer $plainKey")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("filter를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("401을 반환하고 체인을 중단한다") {
                response.status shouldBe 401
                verify(exactly = 0) { filterChain.doFilter(any(), any()) }
            }
        }
    }
})
