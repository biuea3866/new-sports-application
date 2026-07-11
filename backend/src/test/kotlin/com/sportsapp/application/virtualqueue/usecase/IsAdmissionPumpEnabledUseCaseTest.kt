package com.sportsapp.application.virtualqueue.usecase

import com.sportsapp.domain.virtualqueue.service.AdmissionDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

/**
 * `IsAdmissionPumpEnabledUseCase` — `AdmissionDomainService.isPumpEnabled` 위임만 검증한다.
 */
class IsAdmissionPumpEnabledUseCaseTest : BehaviorSpec({

    val admissionDomainService = mockk<AdmissionDomainService>()
    val useCase = IsAdmissionPumpEnabledUseCase(admissionDomainService)

    Given("Admission Pump 킬 스위치가 ON으로 판정되는 상태에서") {
        every { admissionDomainService.isPumpEnabled() } returns true

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("true를 반환한다") {
                result shouldBe true
                verify(exactly = 1) { admissionDomainService.isPumpEnabled() }
            }
        }
    }

    Given("Admission Pump 킬 스위치가 OFF로 판정되는 상태에서 (운영 킬 스위치)") {
        every { admissionDomainService.isPumpEnabled() } returns false

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("false를 반환한다") {
                result shouldBe false
            }
        }
    }
})
