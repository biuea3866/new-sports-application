package com.sportsapp.infrastructure.security

import com.sportsapp.domain.common.security.InternalCallHeaders
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

/**
 * `/internal` 하위 경로 호출자 인증 필터 (S2-07).
 *
 * 검증 대상은 두 가지다 — ① 토큰 없는/틀린 호출을 **404** 로 막는가(403 은 경로 존재를 알려준다)
 * ② 토큰이 맞는 호출에 한해 앞단에서 폐기된 신원 헤더가 **되살아나는가**.
 * ②가 없으면 edge 를 떼는 순간 `/internal/order-history` 경로가 항상 400 이 된다.
 */
class InternalCallAuthenticationFilterTest : DescribeSpec({

    val validToken = "a".repeat(64)

    /** 앞단 sanitizer 를 거친 상태를 재현한다 — 신원 헤더가 폐기된 래퍼가 필터에 도착한다. */
    fun sanitized(request: MockHttpServletRequest): HttpServletRequest =
        InternalIdentityHeaderRequest(request, identity = null)

    fun request(
        path: String,
        callToken: String? = validToken,
        subject: String? = "42",
    ): MockHttpServletRequest = MockHttpServletRequest("GET", path).apply {
        callToken?.let { addHeader(InternalCallHeaders.CALL_TOKEN, it) }
        subject?.let { addHeader(InternalIdentityHeaders.SUBJECT, it) }
    }

    class RecordingChain : FilterChain {
        var invoked = false
        var seenSubject: String? = null
        var seenCallToken: String? = null

        override fun doFilter(request: jakarta.servlet.ServletRequest, response: jakarta.servlet.ServletResponse) {
            invoked = true
            val http = request as HttpServletRequest
            seenSubject = http.getHeader(InternalIdentityHeaders.SUBJECT)
            seenCallToken = http.getHeader(InternalCallHeaders.CALL_TOKEN)
        }
    }

    describe("토큰이 설정된 상태") {
        val filter = InternalCallAuthenticationFilter(configuredToken = validToken)

        it("올바른 토큰이면 통과시키고 폐기된 신원 헤더를 되살린다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods")), response, chain)

            chain.invoked shouldBe true
            chain.seenSubject shouldBe "42"
            response.status shouldBe 200
        }

        it("토큰이 없으면 404 로 막는다 — 경로 존재를 드러내지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods", callToken = null)), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
            response.contentAsString shouldBe ""
        }

        it("토큰이 틀리면 404 로 막는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods", callToken = "b".repeat(64))), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
        }

        it("길이가 다른 토큰도 404 로 막는다 — 비교가 예외로 새지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods", callToken = "short")), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
        }

        it("올바른 토큰이어도 신원 헤더가 없으면 그대로 없는 채 통과한다 — 비로그인 공개 조회 경로") {
            val chain = RecordingChain()

            filter.doFilter(
                sanitized(request("/internal/catalog/goods", subject = null)),
                MockHttpServletResponse(),
                chain,
            )

            chain.invoked shouldBe true
            chain.seenSubject.shouldBeNull()
        }

        it("/internal/alerts/** 는 호출자 토큰 없이도 통과한다 — Grafana 웹훅 계약 보존") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/alerts/webhook", callToken = null)), response, chain)

            chain.invoked shouldBe true
            response.status shouldBe 200
        }

        it("슬래시 없는 정확 경로 /internal/alerts 도 통과한다 — 내부 raise 진입점이 이 경로다") {
            // Spring 의 "/internal/alerts/**" matcher 는 이 경로를 포함하지만 접두사 비교는
            // 놓친다. 실제로 AlertWebhookSecurityScenarioTest 가 이 갭을 404 로 잡아냈다.
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/alerts", callToken = null)), response, chain)

            chain.invoked shouldBe true
            response.status shouldBe 200
        }

        it("이름만 비슷한 /internal/alertsx 는 알림 경로로 보지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/alertsx", callToken = null)), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
        }

        it("/internal/alerts/** 로 들어온 신원 헤더는 되살리지 않는다 — 호출자 인증을 거치지 않았다") {
            val chain = RecordingChain()

            filter.doFilter(
                sanitized(request("/internal/alerts/webhook", callToken = null, subject = "999")),
                MockHttpServletResponse(),
                chain,
            )

            chain.seenSubject.shouldBeNull()
        }

        it("/internal 이 아닌 경로는 손대지 않는다 — 폐기 상태가 그대로 유지된다") {
            val chain = RecordingChain()

            filter.doFilter(
                sanitized(request("/api/catalog", callToken = null, subject = "999")),
                MockHttpServletResponse(),
                chain,
            )

            chain.invoked shouldBe true
            chain.seenSubject.shouldBeNull()
        }

        it("경로 접두사만 같고 실제로는 다른 경로(/internalx)는 내부 경로로 보지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internalx/foo", callToken = null)), response, chain)

            chain.invoked shouldBe true
            response.status shouldBe 200
        }
    }

    describe("토큰 미설정 상태 (INTERNAL_CALL_TOKEN 미주입)") {
        val filter = InternalCallAuthenticationFilter(configuredToken = "")

        it("모든 /internal/** 을 404 로 닫는다 — 미주입이 무방비 개방이 되지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods")), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
        }

        it("빈 토큰을 보내도 통과하지 않는다") {
            val chain = RecordingChain()
            val response = MockHttpServletResponse()

            filter.doFilter(sanitized(request("/internal/catalog/goods", callToken = "")), response, chain)

            chain.invoked shouldBe false
            response.status shouldBe 404
        }

        it("/internal/alerts/** 는 여전히 통과한다 — 자체 시크릿으로 보호된다") {
            val chain = RecordingChain()

            filter.doFilter(
                sanitized(request("/internal/alerts/webhook", callToken = null)),
                MockHttpServletResponse(),
                chain,
            )

            chain.invoked shouldBe true
        }
    }
})
