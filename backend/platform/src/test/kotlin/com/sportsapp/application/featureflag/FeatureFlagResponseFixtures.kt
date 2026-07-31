package com.sportsapp.application.featureflag

import com.sportsapp.domain.featureflag.entity.FeatureFlag
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.strategy.EvaluationStrategy
import java.time.ZonedDateTime

/**
 * 애플리케이션 계층 테스트 공용 픽스처.
 *
 * [FeatureFlag.id]·`createdAt`·`updatedAt`은 JPA 영속화 시점(`@GeneratedValue`/`@CreatedDate`)에만
 * 채워지므로, MockK만 사용하는 단위 테스트에서는 리플렉션으로 강제 주입한다
 * (레포 `McpAuditLog` 테스트 선례와 동일한 패턴).
 */
fun testFeatureFlag(
    flagKey: String = "demo.feature.hello",
    type: FeatureFlagType = FeatureFlagType.RELEASE,
    strategy: EvaluationStrategy = EvaluationStrategy.GlobalToggle(enabled = true),
    description: String? = "테스트용 플래그",
    id: Long = 1L,
    createdAt: ZonedDateTime = ZonedDateTime.now(),
    updatedAt: ZonedDateTime = createdAt,
): FeatureFlag {
    val flag = FeatureFlag.create(flagKey = flagKey, type = type, strategy = strategy, description = description)
    setFeatureFlagField(flag, "id", id)
    setFeatureFlagField(flag, "createdAt", createdAt)
    setFeatureFlagField(flag, "updatedAt", updatedAt)
    return flag
}

private fun setFeatureFlagField(flag: FeatureFlag, fieldName: String, value: Any) {
    val field = FeatureFlag::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    field.set(flag, value)
}
