package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.VerifyPartnerApiKeyCommand
import com.sportsapp.domain.partner.service.PartnerApiKeyVerification
import com.sportsapp.domain.partner.service.PartnerApiKeyVerificationFailure
import com.sportsapp.domain.partner.service.PartnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class VerifyPartnerApiKeyUseCaseTest : BehaviorSpec({

    val partnerDomainService = mockk<PartnerDomainService>()
    val useCase = VerifyPartnerApiKeyUseCase(partnerDomainService)

    Given("유효한 평문 키로 검증을 요청하면") {
        val command = VerifyPartnerApiKeyCommand(plainKey = "partner_60_random")
        every { partnerDomainService.verifyApiKey("partner_60_random") } returns
            PartnerApiKeyVerification.valid(partnerId = 2L, linkedUserId = 88L)

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("valid=true와 파트너 식별자가 담긴 응답이 반환된다") {
                result.valid shouldBe true
                result.partnerId shouldBe 2L
                result.linkedUserId shouldBe 88L
                verify(exactly = 1) { partnerDomainService.verifyApiKey("partner_60_random") }
            }
        }
    }

    Given("존재하지 않는 평문 키로 검증을 요청하면") {
        val command = VerifyPartnerApiKeyCommand(plainKey = "partner_999_random")
        every { partnerDomainService.verifyApiKey("partner_999_random") } returns
            PartnerApiKeyVerification.invalid()

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("예외 없이 valid=false 응답이 반환된다 (실패 경로)") {
                result.valid shouldBe false
                result.failureReason shouldBe PartnerApiKeyVerificationFailure.INVALID
            }
        }
    }

    Given("SUSPENDED 파트너의 키로 검증을 요청하면") {
        val command = VerifyPartnerApiKeyCommand(plainKey = "partner_60_random")
        every { partnerDomainService.verifyApiKey("partner_60_random") } returns
            PartnerApiKeyVerification.suspended()

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("SUSPENDED 사유가 담긴 응답이 반환된다") {
                result.valid shouldBe false
                result.failureReason shouldBe PartnerApiKeyVerificationFailure.SUSPENDED
            }
        }
    }
})
