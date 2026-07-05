package com.sportsapp.infrastructure.partner

import com.sportsapp.domain.partner.service.PartnerDomainService
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class AsyncPartnerApiKeyUsageRecorderTest : BehaviorSpec({

    Given("PartnerDomainService가 정상 동작한다") {
        val partnerDomainService = mockk<PartnerDomainService>()
        val recorder = AsyncPartnerApiKeyUsageRecorder(partnerDomainService)
        every { partnerDomainService.recordKeyUsage(50L) } returns Unit

        When("recordUsage를 호출하면") {
            recorder.recordUsage(keyId = 50L)

            Then("PartnerDomainService.recordKeyUsage가 해당 keyId로 1회 호출된다") {
                verify(exactly = 1) { partnerDomainService.recordKeyUsage(50L) }
            }
        }
    }

    Given("PartnerDomainService가 예외를 던진다") {
        val partnerDomainService = mockk<PartnerDomainService>()
        val recorder = AsyncPartnerApiKeyUsageRecorder(partnerDomainService)
        every { partnerDomainService.recordKeyUsage(999L) } throws RuntimeException("DB 순단")

        When("recordUsage를 호출하면") {
            Then("예외를 전파하지 않는다") {
                shouldNotThrowAny {
                    recorder.recordUsage(keyId = 999L)
                }
            }
        }
    }
})
