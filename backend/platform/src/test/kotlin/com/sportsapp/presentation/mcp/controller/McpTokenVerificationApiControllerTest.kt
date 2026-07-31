package com.sportsapp.presentation.mcp.controller

import com.sportsapp.application.mcp.dto.VerifyMcpTokenCommand
import com.sportsapp.application.mcp.dto.VerifyMcpTokenResponse
import com.sportsapp.application.mcp.usecase.VerifyMcpTokenUseCase
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler

private fun buildMockMvc(verifyMcpTokenUseCase: VerifyMcpTokenUseCase) = MockMvcBuilders.standaloneSetup(
    McpTokenVerificationApiController(verifyMcpTokenUseCase),
).setControllerAdvice(GlobalExceptionHandler()).build()

class McpTokenVerificationApiControllerTest : BehaviorSpec({

    Given("유효한 평문 토큰이 담긴 검증 요청") {
        val verifyMcpTokenUseCase = mockk<VerifyMcpTokenUseCase>()
        val mockMvc = buildMockMvc(verifyMcpTokenUseCase)
        every { verifyMcpTokenUseCase.execute(any()) } returns VerifyMcpTokenResponse(
            valid = true,
            tokenId = 42L,
            userId = 7L,
            scopes = listOf("read:facility"),
        )

        When("POST /internal/mcp-tokens/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/mcp-tokens/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token": "mcp_42_random"}"""),
            )

            Then("200과 검증 결과가 반환된다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.tokenId").value(42))
                    .andExpect(jsonPath("$.userId").value(7))
                    .andExpect(jsonPath("$.scopes[0]").value("read:facility"))
                verify(exactly = 1) { verifyMcpTokenUseCase.execute(any()) }
            }
        }
    }

    Given("존재하지 않거나 만료된 토큰이 담긴 검증 요청") {
        val verifyMcpTokenUseCase = mockk<VerifyMcpTokenUseCase>()
        val mockMvc = buildMockMvc(verifyMcpTokenUseCase)
        every { verifyMcpTokenUseCase.execute(any()) } returns VerifyMcpTokenResponse(
            valid = false,
            tokenId = null,
            userId = null,
            scopes = emptyList(),
        )

        When("POST /internal/mcp-tokens/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/mcp-tokens/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token": "mcp_999_random"}"""),
            )

            Then("예외 없이 200과 valid=false가 반환된다 (실패도 결과값)") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.tokenId").doesNotExist())
            }
        }
    }

    Given("token 필드가 빈 문자열인 검증 요청") {
        val verifyMcpTokenUseCase = mockk<VerifyMcpTokenUseCase>()
        val mockMvc = buildMockMvc(verifyMcpTokenUseCase)

        When("POST /internal/mcp-tokens/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/mcp-tokens/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token": ""}"""),
            )

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { verifyMcpTokenUseCase.execute(any()) }
            }
        }
    }

    Given("평문 토큰을 캡처하는 검증 요청") {
        val verifyMcpTokenUseCase = mockk<VerifyMcpTokenUseCase>()
        val mockMvc = buildMockMvc(verifyMcpTokenUseCase)
        val commandSlot = slot<VerifyMcpTokenCommand>()
        every { verifyMcpTokenUseCase.execute(capture(commandSlot)) } returns VerifyMcpTokenResponse(
            valid = false,
            tokenId = null,
            userId = null,
            scopes = emptyList(),
        )

        When("POST /internal/mcp-tokens/verify 요청 시") {
            val responseBody = mockMvc.perform(
                post("/internal/mcp-tokens/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"token": "mcp_42_super-secret-random-part"}"""),
            ).andReturn().response.contentAsString

            Then("응답 본문에 토큰 원문이 그대로 노출되지 않는다 (보안)") {
                responseBody shouldNotContain "super-secret-random-part"
                commandSlot.captured.plainToken shouldBe "mcp_42_super-secret-random-part"
            }
        }
    }
})
