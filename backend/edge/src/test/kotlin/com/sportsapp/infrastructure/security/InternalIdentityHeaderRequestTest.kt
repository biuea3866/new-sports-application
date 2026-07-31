package com.sportsapp.infrastructure.security

import com.sportsapp.domain.identity.vo.InternalAuthChannel
import com.sportsapp.domain.identity.vo.InternalIdentity
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import org.springframework.mock.web.MockHttpServletRequest

class InternalIdentityHeaderRequestTest : BehaviorSpec({

    fun forgedRequest(): MockHttpServletRequest = MockHttpServletRequest().apply {
        requestURI = "/mcp/tools/list"
        addHeader(InternalIdentityHeaders.SUBJECT, "999")
        addHeader(InternalIdentityHeaders.CHANNEL, InternalAuthChannel.MCP_TOKEN.name)
        addHeader(InternalIdentityHeaders.SCOPES, "write:booking:any")
        addHeader("Authorization", "Bearer mcp_1_secret")
    }

    Given("외부에서 내부 신원 헤더를 위조해 보낸 요청이 신원 없이 감싸이면") {
        val wrapped = InternalIdentityHeaderRequest(forgedRequest(), identity = null)

        Then("위조된 내부 헤더가 전부 폐기되어 조회되지 않는다") {
            wrapped.getHeader(InternalIdentityHeaders.SUBJECT).shouldBeNull()
            wrapped.getHeader(InternalIdentityHeaders.CHANNEL).shouldBeNull()
            wrapped.getHeader(InternalIdentityHeaders.SCOPES).shouldBeNull()
        }

        Then("소문자로 위조해도 폐기된다") {
            wrapped.getHeader(InternalIdentityHeaders.SUBJECT.lowercase()).shouldBeNull()
        }

        Then("헤더 이름 목록에도 내부 헤더가 남지 않는다") {
            wrapped.headerNames.toList() shouldNotContain InternalIdentityHeaders.SUBJECT
        }

        Then("getHeaders 로 값 목록을 훑어도 비어 있다") {
            wrapped.getHeaders(InternalIdentityHeaders.SUBJECT).toList().shouldContainExactly(emptyList())
        }

        Then("내부 헤더가 아닌 헤더는 그대로 보인다") {
            wrapped.getHeader("Authorization") shouldBe "Bearer mcp_1_secret"
        }
    }

    Given("검증된 신원과 함께 요청이 감싸이면") {
        val identity = InternalIdentity(
            subjectId = 10L,
            channel = InternalAuthChannel.MCP_TOKEN,
            scopes = listOf("read:facility", "write:booking:any"),
        )
        val wrapped = InternalIdentityHeaderRequest(forgedRequest(), identity = identity)

        Then("위조된 값이 아니라 검증된 신원 값이 조회된다") {
            wrapped.getHeader(InternalIdentityHeaders.SUBJECT) shouldBe "10"
            wrapped.getHeader(InternalIdentityHeaders.CHANNEL) shouldBe "MCP_TOKEN"
            wrapped.getHeader(InternalIdentityHeaders.SCOPES) shouldBe "read:facility,write:booking:any"
        }

        Then("헤더 이름 목록에 내부 헤더가 한 번씩만 나타난다") {
            val names = wrapped.headerNames.toList()
            names.count { it.equals(InternalIdentityHeaders.SUBJECT, ignoreCase = true) } shouldBe 1
        }
    }

    Given("스코프가 없는 신원이면") {
        val identity = InternalIdentity(
            subjectId = 7L,
            channel = InternalAuthChannel.PARTNER_API_KEY,
            scopes = emptyList(),
        )
        val wrapped = InternalIdentityHeaderRequest(forgedRequest(), identity = identity)

        Then("스코프 헤더는 빈 문자열로 전파된다 — 위조된 스코프가 살아남지 않는다 (엣지)") {
            wrapped.getHeader(InternalIdentityHeaders.SCOPES) shouldBe ""
        }
    }
})
