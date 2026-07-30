package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.dto.ActivateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ActivateFeatureFlagUseCaseTest : BehaviorSpec({

    Given("ARCHIVED 플래그를 activate하는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = ActivateFeatureFlagUseCase(featureFlagDomainService)
        val command = ActivateFeatureFlagCommand(key = "demo.feature.activate", actorUserId = 1L)
        val activatedFlag = testFeatureFlag(flagKey = "demo.feature.activate")
        every { featureFlagDomainService.activate(command) } returns activatedFlag

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService.activate가 1회 호출된다") {
                verify(exactly = 1) { featureFlagDomainService.activate(command) }
            }

            Then("ACTIVE 상태의 FeatureFlagResponse를 반환한다") {
                result.status shouldBe FeatureFlagStatus.ACTIVE
            }
        }
    }
})
