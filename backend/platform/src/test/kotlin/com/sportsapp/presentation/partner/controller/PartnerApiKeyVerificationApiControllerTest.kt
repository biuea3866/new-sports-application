package com.sportsapp.presentation.partner.controller

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyResponse
import com.sportsapp.application.partner.usecase.VerifyPartnerApiKeyUseCase
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.string.shouldNotContain
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import sportsapp.testkit.presentation.exception.GlobalExceptionHandler

private fun buildMockMvc(
    verifyPartnerApiKeyUseCase: VerifyPartnerApiKeyUseCase,
    partnerAuthEnabled: Boolean = true,
) = MockMvcBuilders.standaloneSetup(
    PartnerApiKeyVerificationApiController(verifyPartnerApiKeyUseCase, partnerAuthEnabled),
).setControllerAdvice(GlobalExceptionHandler()).build()

class PartnerApiKeyVerificationApiControllerTest : BehaviorSpec({

    Given("partner.auth.enabled=true 상태에서 유효한 API 키가 담긴 검증 요청") {
        val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
        val mockMvc = buildMockMvc(verifyPartnerApiKeyUseCase, partnerAuthEnabled = true)
        every { verifyPartnerApiKeyUseCase.execute(any()) } returns VerifyPartnerApiKeyResponse(
            valid = true,
            partnerId = 2L,
            linkedUserId = 88L,
            failureReason = null,
        )

        When("POST /internal/partner-api-keys/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/partner-api-keys/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"apiKey": "partner_60_random"}"""),
            )

            Then("200과 파트너 식별자가 담긴 검증 결과가 반환된다") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.valid").value(true))
                    .andExpect(jsonPath("$.partnerId").value(2))
                    .andExpect(jsonPath("$.linkedUserId").value(88))
                verify(exactly = 1) { verifyPartnerApiKeyUseCase.execute(any()) }
            }
        }
    }

    Given("partner.auth.enabled=true 상태에서 존재하지 않는 API 키가 담긴 검증 요청") {
        val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
        val mockMvc = buildMockMvc(verifyPartnerApiKeyUseCase, partnerAuthEnabled = true)
        every { verifyPartnerApiKeyUseCase.execute(any()) } returns VerifyPartnerApiKeyResponse(
            valid = false,
            partnerId = null,
            linkedUserId = null,
            failureReason = PartnerApiKeyVerificationFailure.INVALID,
        )

        When("POST /internal/partner-api-keys/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/partner-api-keys/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"apiKey": "partner_999_random"}"""),
            )

            Then("예외 없이 200과 valid=false가 반환된다 (실패도 결과값)") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.valid").value(false))
                    .andExpect(jsonPath("$.failureReason").value("INVALID"))
            }
        }
    }

    Given("partner.auth.enabled=false 상태에서 유효한 API 키가 담긴 검증 요청") {
        val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
        val mockMvc = buildMockMvc(verifyPartnerApiKeyUseCase, partnerAuthEnabled = false)

        When("POST /internal/partner-api-keys/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/partner-api-keys/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"apiKey": "partner_60_random"}"""),
            )

            Then("UseCase를 호출하지 않고 valid=false를 반환한다 (플래그 계약 보존)") {
                result.andExpect(status().isOk)
                    .andExpect(jsonPath("$.valid").value(false))
                verify(exactly = 0) { verifyPartnerApiKeyUseCase.execute(any()) }
            }
        }
    }

    Given("apiKey 필드가 빈 문자열인 검증 요청") {
        val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
        val mockMvc = buildMockMvc(verifyPartnerApiKeyUseCase, partnerAuthEnabled = true)

        When("POST /internal/partner-api-keys/verify 요청 시") {
            val result = mockMvc.perform(
                post("/internal/partner-api-keys/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"apiKey": ""}"""),
            )

            Then("400을 반환하고 UseCase를 호출하지 않는다") {
                result.andExpect(status().isBadRequest)
                verify(exactly = 0) { verifyPartnerApiKeyUseCase.execute(any()) }
            }
        }
    }

    Given("응답 본문에 API 키 원문이 노출되면 안 되는 검증 요청") {
        val verifyPartnerApiKeyUseCase = mockk<VerifyPartnerApiKeyUseCase>()
        val mockMvc = buildMockMvc(verifyPartnerApiKeyUseCase, partnerAuthEnabled = true)
        every { verifyPartnerApiKeyUseCase.execute(any()) } returns VerifyPartnerApiKeyResponse(
            valid = true,
            partnerId = 2L,
            linkedUserId = 88L,
            failureReason = null,
        )

        When("POST /internal/partner-api-keys/verify 요청 시") {
            val responseBody = mockMvc.perform(
                post("/internal/partner-api-keys/verify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"apiKey": "partner_60_super-secret-random-part"}"""),
            ).andReturn().response.contentAsString

            Then("응답 본문에 API 키 원문이 그대로 노출되지 않는다 (보안)") {
                responseBody shouldNotContain "super-secret-random-part"
            }
        }
    }
})
