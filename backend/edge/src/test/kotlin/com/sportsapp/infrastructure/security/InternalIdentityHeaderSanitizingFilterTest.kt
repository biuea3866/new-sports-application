package com.sportsapp.infrastructure.security

import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class InternalIdentityHeaderSanitizingFilterTest : BehaviorSpec({

    val filter = InternalIdentityHeaderSanitizingFilter()

    Given("내부 신원 헤더를 위조한 요청이 들어오면") {
        val request = MockHttpServletRequest().apply {
            requestURI = "/facilities/1"
            addHeader(InternalIdentityHeaders.SUBJECT, "999")
            addHeader(InternalIdentityHeaders.CHANNEL, "MCP_TOKEN")
        }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>()
        var forwarded: HttpServletRequest? = null
        every { filterChain.doFilter(any(), any()) } answers {
            forwarded = firstArg<HttpServletRequest>()
        }

        When("필터를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("다음 필터에는 내부 헤더가 폐기된 요청이 전달된다") {
                forwarded.shouldBeInstanceOf<InternalIdentityHeaderRequest>()
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.SUBJECT).shouldBeNull()
                requireNotNull(forwarded).getHeader(InternalIdentityHeaders.CHANNEL).shouldBeNull()
            }

            Then("요청은 그대로 downstream 으로 이어진다 — 위조 시도만으로 거부하지 않는다") {
                verify(exactly = 1) { filterChain.doFilter(any(), any()) }
                response.status shouldBe 200
            }
        }
    }

    Given("내부 헤더가 없는 평범한 요청이면") {
        val request = MockHttpServletRequest().apply { requestURI = "/facilities/1" }
        val response = MockHttpServletResponse()
        val filterChain = mockk<FilterChain>(relaxed = true)

        When("필터를 통과하면") {
            filter.doFilter(request, response, filterChain)

            Then("동작 변화 없이 통과한다") {
                verify(exactly = 1) { filterChain.doFilter(any(), any()) }
            }
        }
    }
})
