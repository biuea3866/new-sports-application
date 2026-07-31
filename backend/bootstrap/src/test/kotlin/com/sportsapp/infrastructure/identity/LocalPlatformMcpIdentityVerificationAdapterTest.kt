package com.sportsapp.infrastructure.identity

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.application.mcp.usecase.VerifyMcpTokenUseCase
import com.sportsapp.domain.mcp.service.McpTokenDomainService
import com.sportsapp.infrastructure.security.McpUserPrincipal
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify

/**
 * 1단계 로컬 어댑터 — edge 계약을 platform UseCase 호출로 만족시킨다 (Branch By Abstraction).
 *
 * 2단계에 이 어댑터가 edge 안의 RestClient 구현으로 교체되므로, 여기서 고정하는 것은
 * "edge 계약 ↔ platform 응답" 매핑이다. 검증 판정 자체는 platform 테스트가 소유한다.
 */
class LocalPlatformMcpIdentityVerificationAdapterTest : BehaviorSpec({

    val verifyMcpTokenUseCase = mockk<VerifyMcpTokenUseCase>()
    val mcpTokenDomainService = mockk<McpTokenDomainService>()
    val adapter = LocalPlatformMcpIdentityVerificationAdapter(verifyMcpTokenUseCase, mcpTokenDomainService)

    Given("platform 이 유효 응답을 주면") {
        val plainToken = "mcp_1_validsecret"
        every { verifyMcpTokenUseCase.execute(VerifyMcpTokenCommand(plainToken)) } returns VerifyMcpTokenResponse(
            valid = true,
            tokenId = 1L,
            userId = 10L,
            scopes = listOf("read:facility", "write:booking:any"),
        )

        When("검증하면") {
            val verification = adapter.verify(plainToken)

            Then("주체는 tokenId·userId·스코프를 담은 MCP 주체다") {
                verification.valid shouldBe true
                val principal = verification.principal
                principal.shouldBeInstanceOf<McpUserPrincipal>()
                principal.tokenId shouldBe 1L
                principal.userId shouldBe 10L
                principal.grantedScopes.map { it.asString() }.sorted() shouldContainExactly
                    listOf("read:facility", "write:booking:any")
            }

            Then("주체 식별자는 tokenId 다 — 사용 기록 대상이 토큰이기 때문이다") {
                verification.subjectId shouldBe 1L
            }

            Then("권한은 스코프별 MCP_SCOPE 권한 + ROLE_MCP_TOKEN 이다 (이동 전 필터와 동일)") {
                verification.authorities.sorted() shouldContainExactly listOf(
                    "MCP_SCOPE_READ_FACILITY",
                    "MCP_SCOPE_WRITE_BOOKING",
                    "ROLE_MCP_TOKEN",
                )
            }

            Then("전파용 스코프 문자열은 platform 응답 그대로다") {
                verification.scopes shouldContainExactly listOf("read:facility", "write:booking:any")
            }
        }
    }

    Given("스코프가 없는 유효 토큰이면") {
        val plainToken = "mcp_2_noscope"
        every { verifyMcpTokenUseCase.execute(VerifyMcpTokenCommand(plainToken)) } returns VerifyMcpTokenResponse(
            valid = true,
            tokenId = 2L,
            userId = 20L,
            scopes = emptyList(),
        )

        When("검증하면") {
            val verification = adapter.verify(plainToken)

            Then("ROLE_MCP_TOKEN 만 부여된다 (엣지 — 스코프 0)") {
                verification.authorities shouldContainExactly listOf("ROLE_MCP_TOKEN")
            }
        }
    }

    Given("platform 이 무효 응답을 주면") {
        val plainToken = "mcp_999_bad"
        every { verifyMcpTokenUseCase.execute(VerifyMcpTokenCommand(plainToken)) } returns VerifyMcpTokenResponse(
            valid = false,
            tokenId = null,
            userId = null,
            scopes = emptyList(),
        )

        When("검증하면") {
            val verification = adapter.verify(plainToken)

            Then("주체 없는 무효 결과가 되고 실패 사유는 노출되지 않는다") {
                verification.valid shouldBe false
                verification.principal.shouldBeNull()
                verification.subjectId.shouldBeNull()
                verification.authorities shouldContainExactly emptyList()
            }
        }
    }

    Given("사용 기록을 요청하면") {
        justRun { mcpTokenDomainService.recordUsage(1L) }

        When("recordUsage 를 호출하면") {
            adapter.recordUsage(1L)

            Then("platform 도메인 서비스에 그대로 위임한다") {
                verify(exactly = 1) { mcpTokenDomainService.recordUsage(1L) }
            }
        }
    }
})
