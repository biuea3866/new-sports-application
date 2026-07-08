package com.sportsapp.application.featureflag.usecase

import com.sportsapp.domain.featureflag.service.FeatureFlagDomainService
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifySequence

/**
 * `PropagateFeatureFlagChangeUseCase`는 `FeatureFlagDomainService.propagate(key)`에
 * 그대로 위임한다 — 캐시 갱신·broadcast 자체는 도메인 서비스 책임(BE-04에서 검증됨).
 */
class PropagateFeatureFlagChangeUseCaseTest : BehaviorSpec({

    Given("전파 대상 key가 주어진 상황") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>(relaxed = true)
        val useCase = PropagateFeatureFlagChangeUseCase(featureFlagDomainService)

        When("execute(key)를 호출하면") {
            useCase.execute("demo.feature.propagate-usecase")

            Then("FeatureFlagDomainService.propagate에 동일한 key를 위임한다") {
                verify(exactly = 1) { featureFlagDomainService.propagate("demo.feature.propagate-usecase") }
            }
        }
    }

    Given("서로 다른 key로 두 번 연속 호출하는 상황") {
        val featureFlagDomainService = mockk<FeatureFlagDomainService>(relaxed = true)
        val useCase = PropagateFeatureFlagChangeUseCase(featureFlagDomainService)

        When("execute를 각각 다른 key로 호출하면") {
            useCase.execute("demo.feature.first")
            useCase.execute("demo.feature.second")

            Then("호출된 순서·인자 그대로 propagate에 위임한다") {
                verifySequence {
                    featureFlagDomainService.propagate("demo.feature.first")
                    featureFlagDomainService.propagate("demo.feature.second")
                }
            }
        }
    }
})
