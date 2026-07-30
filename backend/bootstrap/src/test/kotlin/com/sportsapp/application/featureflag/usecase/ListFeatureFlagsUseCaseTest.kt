package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.dto.ListFeatureFlagsCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ListFeatureFlagsUseCaseTest : BehaviorSpec({

    Given("status·type 필터가 있는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = ListFeatureFlagsUseCase(featureFlagDomainService)
        val command = ListFeatureFlagsCommand(status = FeatureFlagStatus.ACTIVE, type = FeatureFlagType.RELEASE)
        val flags = listOf(testFeatureFlag(flagKey = "demo.feature.list"))
        every { featureFlagDomainService.findAll(FeatureFlagStatus.ACTIVE, FeatureFlagType.RELEASE) } returns flags

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("status·type 필터가 DomainService.findAll에 그대로 전달된다") {
                verify(exactly = 1) { featureFlagDomainService.findAll(FeatureFlagStatus.ACTIVE, FeatureFlagType.RELEASE) }
            }

            Then("조회된 FeatureFlag 목록을 FeatureFlagResponse 리스트로 반환한다") {
                result shouldHaveSize 1
                result.single().key shouldBe "demo.feature.list"
            }
        }
    }

    Given("필터에 해당하는 플래그가 없는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = ListFeatureFlagsUseCase(featureFlagDomainService)
        val command = ListFeatureFlagsCommand(status = FeatureFlagStatus.ARCHIVED, type = null)
        every { featureFlagDomainService.findAll(FeatureFlagStatus.ARCHIVED, null) } returns emptyList()

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("빈 리스트를 반환한다") {
                result.shouldBeEmpty()
            }
        }
    }
})
