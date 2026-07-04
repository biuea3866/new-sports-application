package com.sportsapp.domain.featureflag.strategy

/**
 * 피처 플래그 평가 결과.
 */
sealed class FeatureEvaluation {
    data object On : FeatureEvaluation()
    data object Off : FeatureEvaluation()
    data class Assigned(val variantName: String) : FeatureEvaluation()
}
