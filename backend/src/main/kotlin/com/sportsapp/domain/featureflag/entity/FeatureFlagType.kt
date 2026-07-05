package com.sportsapp.domain.featureflag.entity

/**
 * 피처 플래그 종류.
 *
 * `VariantBucketing` 전략은 [EXPERIMENT]에서만 허용된다 ([EvaluationStrategy.VariantBucketing.validateFor]).
 */
enum class FeatureFlagType {
    RELEASE,
    OPERATIONAL,
    EXPERIMENT,
    ENTITLEMENT,
}
