package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.dto.UpdateFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.exception.FeatureFlagStatusConflictException
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class UpdateFeatureFlagUseCaseTest : BehaviorSpec({

    Given("ACTIVE 플래그의 전략을 수정하는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = UpdateFeatureFlagUseCase(featureFlagDomainService)
        val command = UpdateFeatureFlagCommand(
            key = "demo.feature.update",
            strategy = EvaluationStrategy.GlobalToggle(enabled = false),
            description = "updated",
            actorUserId = 2L,
        )
        val updatedFlag = testFeatureFlag(
            flagKey = "demo.feature.update",
            strategy = EvaluationStrategy.GlobalToggle(enabled = false),
            description = "updated",
        )
        every { featureFlagDomainService.update(command) } returns updatedFlag

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("command가 DomainService.update에 그대로 위임된다") {
                verify(exactly = 1) { featureFlagDomainService.update(command) }
            }

            Then("갱신된 FeatureFlag를 담은 FeatureFlagResponse를 반환한다") {
                result.description shouldBe "updated"
                result.strategy shouldBe EvaluationStrategy.GlobalToggle(enabled = false)
            }
        }
    }

    Given("ARCHIVED 플래그를 수정하는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = UpdateFeatureFlagUseCase(featureFlagDomainService)
        val command = UpdateFeatureFlagCommand(
            key = "demo.feature.archived",
            strategy = EvaluationStrategy.GlobalToggle(enabled = false),
            description = "nope",
            actorUserId = 1L,
        )
        every { featureFlagDomainService.update(command) } throws
            FeatureFlagStatusConflictException(command.key, FeatureFlagStatus.ARCHIVED)

        When("execute를 호출하면") {
            Then("DomainService가 던진 409 예외가 그대로 전파된다") {
                shouldThrow<FeatureFlagStatusConflictException> { useCase.execute(command) }
            }
        }
    }
})
