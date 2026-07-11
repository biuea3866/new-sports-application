package com.sportsapp.domain.virtualqueue

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe

/**
 * [VirtualQueueFeatureFlagKeys]의 키가 `FeatureFlag.create`의 `FLAG_KEY_PATTERN`
 * (`^[a-z0-9]+(\.[a-z0-9-]+)*$`)을 준수해 관리 API(`CreateFeatureFlagUseCase`)로 생성
 * 가능한지 검증한다 — 과거 `virtual-queue.enabled`(첫 세그먼트 하이픈)는 이 검증에 실패해
 * 관리 API로 생성이 불가능했고, 테스트는 JDBC 직접 INSERT로 우회했다(BE-08 발견 결함).
 */
class VirtualQueueFeatureFlagKeysTest : BehaviorSpec({

    Given("VirtualQueueFeatureFlagKeys.ENABLED 키로 FeatureFlag를 생성하면") {
        Then("FLAG_KEY_PATTERN 검증을 통과해 예외 없이 생성된다") {
            val flag = FeatureFlag.create(
                flagKey = VirtualQueueFeatureFlagKeys.ENABLED,
                type = FeatureFlagType.OPERATIONAL,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = null,
            )

            flag.flagKey shouldBe VirtualQueueFeatureFlagKeys.ENABLED
        }
    }

    Given("VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED 키로 FeatureFlag를 생성하면") {
        Then("FLAG_KEY_PATTERN 검증을 통과해 예외 없이 생성된다") {
            val flag = FeatureFlag.create(
                flagKey = VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED,
                type = FeatureFlagType.OPERATIONAL,
                strategy = EvaluationStrategy.GlobalToggle(enabled = true),
                description = null,
            )

            flag.flagKey shouldBe VirtualQueueFeatureFlagKeys.ADMISSION_ENABLED
        }
    }
})
