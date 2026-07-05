package com.sportsapp.domain.featureflag.strategy

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.sportsapp.domain.common.FeatureContext
import com.sportsapp.domain.featureflag.entity.FeatureFlagType
import com.sportsapp.domain.featureflag.exception.InvalidEvaluationStrategyException

/**
 * 피처 플래그 평가 전략 — 분기 대신 다형성(sealed class + evaluate 오버라이드).
 *
 * `evaluate`는 flagKey를 salt로 받아 [StableBucketer]에 전달한다 — 동일 userId라도
 * flagKey가 다르면 버킷이 독립적으로 분포한다(플래그 간 상관관계 제거).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "strategyType")
@JsonSubTypes(
    JsonSubTypes.Type(value = EvaluationStrategy.GlobalToggle::class, name = "GLOBAL_TOGGLE"),
    JsonSubTypes.Type(value = EvaluationStrategy.PercentageRollout::class, name = "PERCENTAGE_ROLLOUT"),
    JsonSubTypes.Type(value = EvaluationStrategy.AttributeMatch::class, name = "ATTRIBUTE_MATCH"),
    JsonSubTypes.Type(value = EvaluationStrategy.VariantBucketing::class, name = "VARIANT_BUCKETING"),
)
sealed class EvaluationStrategy {

    abstract fun evaluate(flagKey: String, context: FeatureContext): FeatureEvaluation

    /**
     * 전략 자신의 유효성을 검증한다 (Tell, Don't Ask) — 위반 시 [InvalidEvaluationStrategyException].
     * @param type 소속 [FeatureFlag]의 종류 — VariantBucketing의 EXPERIMENT 게이팅에 필요한 협력자.
     */
    abstract fun validateFor(type: FeatureFlagType)

    data class GlobalToggle(val enabled: Boolean) : EvaluationStrategy() {
        override fun evaluate(flagKey: String, context: FeatureContext): FeatureEvaluation =
            if (enabled) FeatureEvaluation.On else FeatureEvaluation.Off

        override fun validateFor(type: FeatureFlagType) = Unit
    }

    data class PercentageRollout(val percentage: Int) : EvaluationStrategy() {
        override fun evaluate(flagKey: String, context: FeatureContext): FeatureEvaluation {
            val userId = context.userId ?: return FeatureEvaluation.Off
            val bucket = StableBucketer.bucket(flagKey, userId)
            return if (bucket < percentage) FeatureEvaluation.On else FeatureEvaluation.Off
        }

        override fun validateFor(type: FeatureFlagType) {
            if (percentage !in MIN_PERCENTAGE..MAX_PERCENTAGE) {
                throw InvalidEvaluationStrategyException(
                    "PercentageRollout.percentage must be within $MIN_PERCENTAGE..$MAX_PERCENTAGE, got $percentage",
                )
            }
        }

        companion object {
            private const val MIN_PERCENTAGE = 0
            private const val MAX_PERCENTAGE = 100
        }
    }

    data class AttributeMatch(val attribute: String, val value: String) : EvaluationStrategy() {
        override fun evaluate(flagKey: String, context: FeatureContext): FeatureEvaluation =
            if (context.attributes[attribute] == value) FeatureEvaluation.On else FeatureEvaluation.Off

        override fun validateFor(type: FeatureFlagType) {
            if (attribute.isBlank()) {
                throw InvalidEvaluationStrategyException("AttributeMatch.attribute must not be blank")
            }
        }
    }

    data class VariantBucketing(val variants: List<Variant>) : EvaluationStrategy() {
        override fun evaluate(flagKey: String, context: FeatureContext): FeatureEvaluation {
            val userId = context.userId ?: return FeatureEvaluation.Off
            val bucket = StableBucketer.bucket(flagKey, userId)
            val assignedVariantName = resolveVariantName(bucket)
            return assignedVariantName?.let { FeatureEvaluation.Assigned(it) } ?: FeatureEvaluation.Off
        }

        private fun resolveVariantName(bucket: Int): String? {
            var cumulativeWeight = 0
            for (variant in variants) {
                cumulativeWeight += variant.weight
                if (bucket < cumulativeWeight) return variant.name
            }
            return null
        }

        override fun validateFor(type: FeatureFlagType) {
            if (type != FeatureFlagType.EXPERIMENT) {
                throw InvalidEvaluationStrategyException(
                    "VariantBucketing is only allowed for EXPERIMENT type, got $type",
                )
            }
            if (variants.isEmpty() || variants.size > MAX_VARIANTS) {
                throw InvalidEvaluationStrategyException(
                    "VariantBucketing.variants count must be within 1..$MAX_VARIANTS, got ${variants.size}",
                )
            }
            val totalWeight = variants.sumOf { it.weight }
            if (totalWeight != TOTAL_WEIGHT) {
                throw InvalidEvaluationStrategyException(
                    "VariantBucketing.variants weight sum must equal $TOTAL_WEIGHT, got $totalWeight",
                )
            }
        }

        companion object {
            const val MAX_VARIANTS = 4
            const val TOTAL_WEIGHT = 100
        }
    }
}

data class Variant(val name: String, val weight: Int)
