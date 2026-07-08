package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.dto.CreateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.exception.DuplicateFeatureFlagKeyException
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class CreateFeatureFlagUseCaseTest : BehaviorSpec({

    Given("신규 플래그 생성 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = CreateFeatureFlagUseCase(featureFlagDomainService)
        val command = CreateFeatureFlagCommand(
            flagKey = "demo.feature.hello",
            type = FeatureFlagType.RELEASE,
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
            description = "demo flag",
            actorUserId = 1L,
        )
        val createdFlag = testFeatureFlag(flagKey = "demo.feature.hello")
        every { featureFlagDomainService.create(command) } returns createdFlag

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("command가 DomainService.create에 그대로 위임된다") {
                verify(exactly = 1) { featureFlagDomainService.create(command) }
            }

            Then("생성된 FeatureFlag를 담은 FeatureFlagResponse를 반환한다") {
                result.key shouldBe "demo.feature.hello"
                result.status shouldBe createdFlag.status
            }
        }
    }

    Given("이미 존재하는 key로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = CreateFeatureFlagUseCase(featureFlagDomainService)
        val command = CreateFeatureFlagCommand(
            flagKey = "demo.feature.duplicate",
            type = FeatureFlagType.RELEASE,
            strategy = EvaluationStrategy.GlobalToggle(enabled = true),
            description = null,
            actorUserId = 1L,
        )
        every { featureFlagDomainService.create(command) } throws DuplicateFeatureFlagKeyException(command.flagKey)

        When("execute를 호출하면") {
            Then("DomainService가 던진 예외가 그대로 전파된다") {
                shouldThrow<DuplicateFeatureFlagKeyException> { useCase.execute(command) }
            }
        }
    }
})
