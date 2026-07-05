package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class DetectStaleFeatureFlagsUseCaseTest : BehaviorSpec({

    Given("정리 후보 플래그가 존재하는 상황") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = DetectStaleFeatureFlagsUseCase(featureFlagDomainService)
        val staleFlags = listOf(testFeatureFlag(flagKey = "demo.feature.stale"))
        every { featureFlagDomainService.findStaleReleaseFlags() } returns staleFlags

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("DomainService.findStaleReleaseFlags를 1회 위임 호출한다") {
                verify(exactly = 1) { featureFlagDomainService.findStaleReleaseFlags() }
            }

            Then("조회된 FeatureFlag 목록을 FeatureFlagResponse 리스트로 반환한다") {
                result shouldHaveSize 1
                result.single().key shouldBe "demo.feature.stale"
            }
        }
    }

    Given("정리 후보가 없는 상황") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = DetectStaleFeatureFlagsUseCase(featureFlagDomainService)
        every { featureFlagDomainService.findStaleReleaseFlags() } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute()

            Then("빈 리스트를 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
