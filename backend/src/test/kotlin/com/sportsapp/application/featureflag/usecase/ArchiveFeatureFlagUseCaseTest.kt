package com.sportsapp.application.featureflag.usecase

import com.sportsapp.application.featureflag.testFeatureFlag
import com.sportsapp.domain.featureflag.dto.ArchiveFeatureFlagCommand
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

class ArchiveFeatureFlagUseCaseTest : BehaviorSpec({

    Given("ACTIVE 플래그를 archive하는 command로 execute를 호출하면") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>()
        val useCase = ArchiveFeatureFlagUseCase(featureFlagDomainService)
        val command = ArchiveFeatureFlagCommand(key = "demo.feature.archive", actorUserId = 1L)
        val archivedFlag = testFeatureFlag(flagKey = "demo.feature.archive").also { it.archive() }
        every { featureFlagDomainService.archive(command) } returns archivedFlag

        When("execute를 호출하면") {
            val result = useCase.execute(command)

            Then("DomainService.archive가 1회 호출된다") {
                verify(exactly = 1) { featureFlagDomainService.archive(command) }
            }

            Then("ARCHIVED 상태의 FeatureFlagResponse를 반환한다") {
                result.status shouldBe FeatureFlagStatus.ARCHIVED
            }
        }
    }
})
