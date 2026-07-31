package com.sportsapp.infrastructure.featureflag

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagStatus
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import com.sportsapp.domain.featureflag.vo.FeatureFlagSnapshot

/**
 * BE-03 Redis 캐시/pub-sub 테스트 공용 픽스처.
 */
fun sampleFeatureFlagSnapshot(
    key: String = "demo.feature.hello",
    enabled: Boolean = true,
): FeatureFlagSnapshot = FeatureFlagSnapshot(
    key = key,
    type = FeatureFlagType.RELEASE,
    status = FeatureFlagStatus.ACTIVE,
    strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
    description = "테스트용 데모 플래그",
)

fun sampleFeatureFlag(
    key: String = "demo.feature.hello",
    enabled: Boolean = true,
): FeatureFlag = FeatureFlag.create(
    flagKey = key,
    type = FeatureFlagType.RELEASE,
    strategy = EvaluationStrategy.GlobalToggle(enabled = enabled),
    description = "테스트용 데모 플래그",
)
