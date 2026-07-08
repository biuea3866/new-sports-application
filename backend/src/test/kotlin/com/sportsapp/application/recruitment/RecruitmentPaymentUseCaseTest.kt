package com.sportsapp.application.recruitment

import com.sportsapp.application.recruitment.usecase.CancelRecruitmentPaymentUseCase
import com.sportsapp.application.recruitment.usecase.ConfirmRecruitmentPaymentUseCase
import com.sportsapp.domain.recruitment.entity.Application
import com.sportsapp.domain.recruitment.service.RecruitmentDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class RecruitmentPaymentUseCaseTest : BehaviorSpec({

    Given("확정 UseCase") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = ConfirmRecruitmentPaymentUseCase(recruitmentDomainService)
        every { recruitmentDomainService.confirmApplication(40L, 400L) } returns mockk<Application>()

        When("execute(orderId, paymentId) 를 호출하면") {
            useCase.execute(40L, 400L)

            Then("RecruitmentDomainService.confirmApplication 에 paymentId 를 전달해 위임한다") {
                verify(exactly = 1) { recruitmentDomainService.confirmApplication(40L, 400L) }
            }
        }
    }

    Given("취소 UseCase") {
        val recruitmentDomainService = mockk<RecruitmentDomainService>()
        val useCase = CancelRecruitmentPaymentUseCase(recruitmentDomainService)
        every { recruitmentDomainService.cancelPendingApplication(41L) } returns mockk<Application>()

        When("execute(orderId) 를 호출하면") {
            useCase.execute(41L)

            Then("RecruitmentDomainService.cancelPendingApplication 에 위임한다") {
                verify(exactly = 1) { recruitmentDomainService.cancelPendingApplication(41L) }
            }
        }
    }
})
