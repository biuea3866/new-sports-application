package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.ReissueApiKeyCommand
import com.sportsapp.domain.partner.entity.ApiKeyStatus
import com.sportsapp.domain.partner.entity.PartnerApiKey
import com.sportsapp.domain.partner.service.IssuedApiKey
import com.sportsapp.domain.partner.service.PartnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ReissueApiKeyUseCaseTest : BehaviorSpec({

    val partnerDomainService = mockk<PartnerDomainService>()
    val useCase = ReissueApiKeyUseCase(partnerDomainService)

    Given("ADMIN이 특정 Partner의 API Key 재발급을 요청할 때") {
        val partnerId = 10L
        val command = ReissueApiKeyCommand(partnerId = partnerId)
        val issuedApiKey = IssuedApiKey(
            plainKey = "partner_2_new-random",
            apiKey = PartnerApiKey.reconstitute(
                id = 2L,
                partnerId = partnerId,
                keyHash = "hashed-new-key",
                status = ApiKeyStatus.ACTIVE,
                revokedAt = null,
                lastUsedAt = null,
            ),
        )
        every { partnerDomainService.reissueKey(partnerId) } returns issuedApiKey

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("PartnerDomainService.reissueKey에 위임한다") {
                verify(exactly = 1) { partnerDomainService.reissueKey(partnerId) }
            }

            Then("새 plainApiKey와 keyId를 반환한다") {
                result.keyId shouldBe 2L
                result.plainApiKey shouldBe "partner_2_new-random"
            }
        }
    }
})
