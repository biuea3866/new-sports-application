package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.RevokeApiKeyCommand
import com.sportsapp.domain.partner.service.PartnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify

class RevokeApiKeyUseCaseTest : BehaviorSpec({

    val partnerDomainService = mockk<PartnerDomainService>()
    val useCase = RevokeApiKeyUseCase(partnerDomainService)

    Given("ADMIN이 지정 Partner의 API Key 폐기를 요청할 때") {
        val partnerId = 10L
        val keyId = 2L
        val command = RevokeApiKeyCommand(partnerId = partnerId, keyId = keyId)
        every { partnerDomainService.revokeKey(partnerId, keyId) } just Runs

        When("execute를 호출하면") {
            useCase.execute(command)

            Then("PartnerDomainService.revokeKey를 정확한 인자로 호출한다") {
                verify(exactly = 1) { partnerDomainService.revokeKey(partnerId, keyId) }
            }
        }
    }
})
