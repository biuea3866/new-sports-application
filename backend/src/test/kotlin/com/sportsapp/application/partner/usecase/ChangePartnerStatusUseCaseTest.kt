package com.sportsapp.application.partner.usecase

import com.sportsapp.application.partner.dto.ChangePartnerStatusCommand
import com.sportsapp.domain.partner.entity.PartnerStatus
import com.sportsapp.domain.partner.service.PartnerDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import io.mockk.verify

class ChangePartnerStatusUseCaseTest : BehaviorSpec({

    val partnerDomainService = mockk<PartnerDomainService>()
    val useCase = ChangePartnerStatusUseCase(partnerDomainService)

    Given("ADMIN이 Partner를 SUSPENDED 상태로 변경 요청할 때") {
        val partnerId = 10L
        val command = ChangePartnerStatusCommand(partnerId = partnerId, status = PartnerStatus.SUSPENDED)
        every { partnerDomainService.changeStatus(partnerId, PartnerStatus.SUSPENDED) } just Runs

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("PartnerDomainService.changeStatus를 정확한 인자로 호출한다") {
                verify(exactly = 1) { partnerDomainService.changeStatus(partnerId, PartnerStatus.SUSPENDED) }
            }

            Then("응답 status가 SUSPENDED이다") {
                result.partnerId shouldBe partnerId
                result.status shouldBe PartnerStatus.SUSPENDED
            }
        }
    }

    Given("ADMIN이 Partner를 ACTIVE 상태로 변경 요청할 때") {
        val partnerId = 11L
        val command = ChangePartnerStatusCommand(partnerId = partnerId, status = PartnerStatus.ACTIVE)
        every { partnerDomainService.changeStatus(partnerId, PartnerStatus.ACTIVE) } just Runs

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("응답 status가 ACTIVE이다") {
                result.partnerId shouldBe partnerId
                result.status shouldBe PartnerStatus.ACTIVE
            }
        }
    }
})
