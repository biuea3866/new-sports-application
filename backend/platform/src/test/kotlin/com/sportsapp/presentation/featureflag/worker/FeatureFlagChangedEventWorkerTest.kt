package com.sportsapp.presentation.featureflag.worker

import com.sportsapp.application.featureflag.usecase.PropagateFeatureFlagChangeUseCase
import com.sportsapp.domain.featureflag.event.FeatureFlagChangedEvent
import io.kotest.assertions.throwables.shouldNotThrowAny
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder

/**
 * `FeatureFlagChangedEventWorker`의 핸들러 로직(AFTER_COMMIT 바인딩 자체는
 * `FeatureFlagChangedEventWorkerIntegrationTest`가 실 트랜잭션으로 검증한다) 단위 테스트.
 */
class FeatureFlagChangedEventWorkerTest : BehaviorSpec({

    Given("FeatureFlagChangedEvent가 수신된 상황") {
        val propagateFeatureFlagChangeUseCase = mockk<PropagateFeatureFlagChangeUseCase>(relaxed = true)
        val worker = FeatureFlagChangedEventWorker(propagateFeatureFlagChangeUseCase)
        val event = FeatureFlagChangedEvent(aggregateId = 1L, flagKey = "demo.feature.worker")

        When("onFeatureFlagChanged를 호출하면") {
            worker.onFeatureFlagChanged(event)

            Then("PropagateFeatureFlagChangeUseCase.execute를 이벤트의 flagKey로 호출한다") {
                verify(exactly = 1) { propagateFeatureFlagChangeUseCase.execute("demo.feature.worker") }
            }
        }
    }

    Given("서로 다른 flagKey의 이벤트가 순서대로 들어오는 상황") {
        val propagateFeatureFlagChangeUseCase = mockk<PropagateFeatureFlagChangeUseCase>(relaxed = true)
        val worker = FeatureFlagChangedEventWorker(propagateFeatureFlagChangeUseCase)

        When("두 이벤트를 순서대로 처리하면") {
            worker.onFeatureFlagChanged(FeatureFlagChangedEvent(aggregateId = 1L, flagKey = "demo.feature.first"))
            worker.onFeatureFlagChanged(FeatureFlagChangedEvent(aggregateId = 2L, flagKey = "demo.feature.second"))

            Then("각 이벤트의 flagKey로 순서대로 위임한다") {
                verifyOrder {
                    propagateFeatureFlagChangeUseCase.execute("demo.feature.first")
                    propagateFeatureFlagChangeUseCase.execute("demo.feature.second")
                }
            }
        }
    }

    Given("PropagateFeatureFlagChangeUseCase.execute가 예외를 던지는 상황") {
        val propagateFeatureFlagChangeUseCase = mockk<PropagateFeatureFlagChangeUseCase>()
        every { propagateFeatureFlagChangeUseCase.execute(any()) } throws IllegalStateException("전파 실패")
        val worker = FeatureFlagChangedEventWorker(propagateFeatureFlagChangeUseCase)
        val event = FeatureFlagChangedEvent(aggregateId = 1L, flagKey = "demo.feature.failure")

        When("onFeatureFlagChanged를 호출하면") {
            Then("예외를 삼키고 리스너 밖으로 전파하지 않는다") {
                shouldNotThrowAny { worker.onFeatureFlagChanged(event) }
            }
        }
    }
})
