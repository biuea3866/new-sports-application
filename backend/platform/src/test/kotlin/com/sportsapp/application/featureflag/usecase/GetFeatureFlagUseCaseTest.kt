package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.exception.FeatureFlagNotFoundException
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk

class GetFeatureFlagUseCaseTest : BehaviorSpec({

    Given("존재하는 key로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = GetFeatureFlagUseCase(featureFlagDomainService)
        val flag = testFeatureFlag(flagKey = "demo.feature.get")
        every { featureFlagDomainService.getByKey("demo.feature.get") } returns flag

        When("execute를 호출하면") {
            val result = useCase.execute("demo.feature.get")

            Then("해당 FeatureFlag를 담은 FeatureFlagResponse를 반환한다") {
                result.key shouldBe "demo.feature.get"
            }
        }
    }

    Given("존재하지 않는 key로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = GetFeatureFlagUseCase(featureFlagDomainService)
        every { featureFlagDomainService.getByKey("demo.feature.missing") } throws
            FeatureFlagNotFoundException("demo.feature.missing")

        When("execute를 호출하면") {
            Then("DomainService가 던진 404 예외가 그대로 전파된다") {
                shouldThrow<FeatureFlagNotFoundException> { useCase.execute("demo.feature.missing") }
            }
        }
    }
})
